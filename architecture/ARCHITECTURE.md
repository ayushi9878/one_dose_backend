# CareFlow architecture

Design decisions and the reasoning behind them.

---

## 1. System context

```
┌──────────────┐         ┌──────────────────────────────────────────┐
│   Browser    │         │            GCE VM (Ubuntu 22.04)         │
│  Next.js SPA │         │                                          │
└──────┬───────┘         │  ┌─────────┐   127.0.0.1:8080            │
       │                 │  │  Nginx  │────────┐                    │
       │  HTTPS :443     │  │  :80    │        ▼                    │
       └────────────────▶│  │  :443   │   ┌──────────────┐          │
                         │  └─────────┘   │ Spring Boot  │          │
                         │                │  (Docker)    │          │
                         │                └──────┬───────┘          │
                         │                       │ :3306            │
                         │                       ▼                  │
                         │                ┌──────────────┐          │
                         │                │   MySQL 8    │          │
                         │                └──────────────┘          │
                         └──────────────────────────────────────────┘
```

**Why Nginx in front.** Spring Boot publishes only to `127.0.0.1:8080`, so the
application port is unreachable from the internet regardless of firewall
mistakes. TLS termination, HSTS, security headers and auth rate limiting live at
the edge where they belong, and the application never handles certificates.

---

## 2. Backend layering

```
HTTP ──▶ Controller ──▶ Service ──▶ Repository ──▶ Database
             │             │
             │             ├──▶ AuditService
             │             └──▶ Mapper ──▶ DTO
             └── validation (@Valid)
```

**Rules held throughout:**

- Controllers contain no business logic — they validate, delegate, and shape the HTTP response.
- Services own transaction boundaries and every workflow invariant.
- JPA entities never cross the API boundary; every response is a DTO record.
- Constructor injection only, so dependencies are explicit and testable.
- `@RestControllerAdvice` produces one consistent error envelope.

**Why DTOs rather than entities.** Serialising entities couples the wire format
to the schema, leaks lazy-loading behaviour into JSON, and risks exposing fields
like `passwordHash` by accident. Records make the API contract explicit.

---

## 3. The transactional workflow

The follow-up response is the system's critical path:

```
@Transactional submitResponse()
   │
   ├─ save PatientResponse
   ├─ mark FollowUpTask COMPLETED
   ├─ audit RESPONSE_RECEIVED
   ├─ AdherenceService.recordFromResponse()   ──▶ AdherenceEvent
   ├─ RiskEvaluationService.evaluate()        ──▶ RiskEvaluationResult (pure)
   ├─ persist RiskSignal per signal            ──▶ audit RISK_SIGNAL_CREATED
   ├─ EscalationService.createFromRiskEvaluation()  (only if review required)
   └─ update Patient.currentRiskLevel
```

**Why one transaction.** Partial completion here is a patient-safety problem:
a stored response whose risk evaluation was skipped is an alert that silently
never fires. Committing everything together makes that state unrepresentable.

`AuditService` methods are annotated `Propagation.MANDATORY` — they *cannot* run
outside a caller's transaction. An audit row can never record an action that was
subsequently rolled back.

---

## 4. The rules engine

`RiskEvaluationService` takes `RiskEvaluationInput` (an immutable record) and
returns `RiskEvaluationResult`. It touches no repository, no clock and no
external service.

**Why deterministic and pure:**

1. **Auditability.** A regulator asking "why was this case escalated on 3 March?" gets a reproducible answer from the stored inputs.
2. **Testability.** Every rule and combination is unit-tested without a database.
3. **Safety.** No model sits in the decision path, so behaviour cannot drift between deployments.

Thresholds are configuration (`RISK_MISSED_DOSE_THRESHOLD`,
`ADHERENCE_LOW_THRESHOLD`), so clinical operations can tune sensitivity without a
code change.

**Where AI is allowed.** Only in `CaseSummaryService`, which writes a *briefing*
for the human reviewer after the decision is already made. It cannot change the
risk level, cannot open or close a case, and falls back to a deterministic
template on any error.

---

## 5. Adherence calculation

Naively, `missedDoses` from a patient response could be treated as the whole
story. That breaks down immediately: three missed doses means something very
different for a once-weekly tablet than for a four-times-daily one.

Instead, expected doses are **projected**:

```
expectedDoses = Σ (medication.dosesPerDay × days active in the interval)
```

over each *active* prescription, clamped to the medication's own start and end
dates, across the window the follow-up covers. `MedicationFrequency` carries
`dosesPerDay` as data, so adding a frequency cannot silently produce a zero.

Two deliberate guards:

- Missed doses are capped at expected doses — a data-entry slip cannot produce negative adherence.
- A patient with no recorded doses returns `null`, not `0%`. "No data" and "zero adherence" are different clinical facts and must not be conflated.

---

## 6. Authorization model

Three roles, with scoping enforced in the **service layer** rather than by URL:

| Role | Reach |
|---|---|
| `ADMIN` | Everything |
| `CARE_MANAGER` | Only patients assigned to them |
| `PATIENT` | Only their own record |

`PatientService.requireAccessiblePatient()` is the single gate every
patient-scoped operation passes through — medications, follow-ups, adherence and
audit all call it. A new endpoint that forgets an `@PreAuthorize` still cannot
read another care manager's caseload, because the check lives where the data is
loaded.

The escalation queue is deliberately *shared*: every care manager sees unassigned
cases so none goes unowned, with `assignedToMe` to narrow the view.

---

## 7. Data model decisions

**Enums as strings.** `EnumType.STRING` costs a few bytes per row and removes an
entire class of bug: with ordinals, inserting a constant silently reinterprets
every existing row.

**Soft deletes.** Patients deactivate and medications discontinue rather than
being removed, so historical adherence stays explainable.

**Audit rows carry no foreign keys.** `audit_events` stores `patient_id` and
`actor_id` as plain columns. The trail must survive deletion of the records it
describes — a foreign key would either block the delete or cascade away the
evidence.

**Optimistic locking.** Every entity carries `@Version`, so two care managers
resolving the same escalation cannot silently overwrite each other.

---

## 8. Deployment strategy

```
                    ┌─────────────────────┐
                    │ record current image│
                    └──────────┬──────────┘
                               ▼
                    ┌─────────────────────┐
                    │ pull + start new    │
                    └──────────┬──────────┘
                               ▼
                    ┌─────────────────────┐
                    │ poll /actuator/health│  (120s budget)
                    └──────────┬──────────┘
                     healthy?  │
                 ┌─────────────┴─────────────┐
                YES                          NO
                 │                            │
        keep new, prune old        restart previous image,
                                     exit non-zero (build red)
```

**Why deploy the commit-SHA tag rather than `:latest`.** `:latest` is mutable —
two deploys minutes apart can produce different containers. The SHA tag proves
the VM runs exactly the artifact the pipeline tested, and the prior SHA is always
available for rollback.

**Why verify independently after deploying.** `deploy.sh` already gates on
health, but "a container started and reports UP" is weaker than "the new code is
serving traffic". The `verify` step re-reads `/api/system/version` and asserts
the commit matches, catching a silently-not-replaced container.

---

## 9. Frontend architecture

```
app/(app)/*  ──▶ AppShell (auth guard, nav)
                     │
                     ├── useApiResource()  ──▶ services/careflow-api.ts
                     │                              │
                     └── UI components               ▼
                                            services/api-client.ts
                                            (JWT, errors, 401 handling)
```

**One HTTP chokepoint.** `api-client.ts` attaches the token, normalises errors
into `ApiRequestError`, and handles 401 globally by clearing the session and
redirecting. No component deals with raw `fetch`.

**Session restore validates server-side.** On load the app calls `/api/auth/me`
rather than trusting the stored token, so a revoked or expired token cannot leave
a stale user in the UI.

**Loading, empty and error states are explicit** on every data surface — a
healthcare operator must never be unable to tell "no escalations" from "failed
to load escalations".

**Chart colours are validated, not chosen by eye.** The palette passes the
lightness band, chroma floor, CVD separation (worst ΔE 9.2 deutan) and
normal-vision floor (worst ΔE 27.6) checks against the app's surface. Where a
hue falls below 3:1 contrast, the chart ships visible labels and a table view, so
meaning never rests on colour alone.

---

## 10. Known limitations

Honest boundaries of the current implementation:

- **Adherence is self-reported.** There is no pharmacy refill feed or smart-dispenser integration, so figures reflect what patients say.
- **Single VM.** No horizontal scaling or load balancer; adequate for the demonstrated scale, but a managed instance group would be the next step.
- **No notification delivery.** Follow-ups are scheduled and surfaced in-app; SMS/email dispatch is not implemented.
- **The overdue sweep is a single-node cron.** Running multiple instances would need a shared lock (ShedLock or equivalent).
- **Integration tests run on H2.** MySQL-specific behaviour is covered by running the Flyway migration against real MySQL in Compose, not in the CI test suite. Testcontainers would close this gap where a Docker daemon is available.
