# CareFlow

**Continuity care, connected.**

A continuity-care workflow platform for post-discharge follow-up, medication
adherence and human escalation. CareFlow tracks what happens to a patient *after*
they leave hospital: it schedules follow-ups, records what patients report,
calculates adherence, applies deterministic operational rules, and routes cases
that need attention to a qualified care manager.

> **Clinical safety boundary.** CareFlow does not diagnose conditions or
> recommend treatment. The risk engine emits *operational* signals only —
> "three or more missed doses", "symptoms self-reported", "refill needed" — and
> every flagged case is routed to a human who makes the care decision. This is a
> deliberate architectural constraint, enforced by keeping the rules engine a
> pure, deterministic function with no model in the decision path.

---

## Contents

1. [The problem](#1-the-problem)
2. [Architecture](#2-architecture)
3. [Technology stack](#3-technology-stack)
4. [Domain model](#4-domain-model)
5. [The core workflow](#5-the-core-workflow)
6. [API reference](#6-api-reference)
7. [Local setup](#7-local-setup)
8. [Environment configuration](#8-environment-configuration)
9. [Docker](#9-docker)
10. [GCE architecture](#10-gce-architecture)
11. [Artifact Registry](#11-artifact-registry)
12. [Cloud Build CI/CD](#12-cloud-build-cicd)
13. [Deployment and rollback](#13-deployment-and-rollback)
14. [Security](#14-security)
15. [Testing](#15-testing)
16. [Demo walkthrough](#16-demo-walkthrough)
17. [Project layout](#17-project-layout)

---

## 1. The problem

Most avoidable readmissions are not caused by a lack of clinical knowledge — they
are caused by a break in *continuity*. A patient is discharged with a
prescription and an instruction sheet, and then nobody hears from them until
something goes wrong.

CareFlow closes three specific gaps:

| Gap | How CareFlow addresses it |
|---|---|
| Nobody follows up on schedule | Discharge automatically generates a follow-up plan (days 1/7/14/30, plus day 3 for high-risk patients) |
| Missed doses go unnoticed | Every response records an adherence event; percentages are computed server-side from stored dose counts |
| Concerning reports reach nobody | Deterministic rules open an escalation and route it to a named care manager, who must resolve it with notes |

Everything is written to an append-only audit trail, so the full history of a
patient's care is reconstructable.

---

## 2. Architecture

```
                            ┌──────────────────────┐
   Browser  ───── HTTPS ───▶│  Nginx (443)         │
                            │  TLS, rate limits    │
                            └──────────┬───────────┘
                                       │  127.0.0.1:8080
                                       ▼
                            ┌──────────────────────┐
                            │  Spring Boot         │
                            │  (Docker, non-root)  │
                            └──────────┬───────────┘
                                       │
                                       ▼
                            ┌──────────────────────┐
                            │  MySQL 8             │
                            └──────────────────────┘
```

Spring Boot binds to loopback only and is never published to the internet; the
firewall opens ports 22 (IAP-restricted), 80 and 443 exclusively.

### Backend layering

```
controller  ─▶  service  ─▶  repository  ─▶  entity
     │             │
     │             └── mapper ──▶ dto
     └── dto
```

Controllers do no business logic. Services own transactions and workflow rules.
Entities are never serialised to the API — every response is a DTO.

---

## 3. Technology stack

**Backend** — Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA, Hibernate,
Spring Security, JJWT, MySQL 8, Flyway, Jakarta Bean Validation, Lombok, Maven,
JUnit 5, Mockito, Actuator, springdoc-openapi.

**Frontend** — Next.js 16 (App Router), React 19, TypeScript (strict), Tailwind
CSS, Recharts.

**Infrastructure** — Docker (multi-stage, non-root), Docker Compose, Nginx,
Google Compute Engine, Artifact Registry, Cloud Build, Secret Manager.

---

## 4. Domain model

```
User ──1:1──▶ Patient ──┬──▶ Medication
                        ├──▶ CarePlan ──▶ FollowUpTask ──▶ PatientResponse
                        ├──▶ AdherenceEvent
                        ├──▶ RiskSignal
                        ├──▶ Escalation
                        └──▶ AuditEvent
```

| Table | Purpose |
|---|---|
| `users` | Accounts and roles (ADMIN, CARE_MANAGER, PATIENT) |
| `patients` | Demographics, discharge date, current risk level |
| `medications` | Prescriptions with dosing frequency |
| `care_plans` | The active plan and its type |
| `follow_up_tasks` | Scheduled touchpoints in the care journey |
| `patient_responses` | What the patient reported |
| `adherence_events` | Point-in-time dose observations |
| `risk_signals` | Operational signals raised by the rules engine |
| `escalations` | Cases awaiting or under human review |
| `audit_events` | Append-only workflow history |

The schema lives in [`V1__baseline_schema.sql`](backend/src/main/resources/db/migration/V1__baseline_schema.sql)
and is applied by Flyway. All tables carry `created_at`, `updated_at`, an
optimistic-locking `version`, foreign keys and query-shaped indexes.

**Enums** are stored as strings (`EnumType.STRING`) rather than ordinals, so
adding a constant can never silently reinterpret existing rows.

---

## 5. The core workflow

Submitting a follow-up response runs this chain **in a single transaction**:

```
POST /api/follow-ups/{id}/response
        │
        ├─▶ store the response
        ├─▶ mark the follow-up completed
        ├─▶ record an adherence event   (expected doses projected from prescriptions)
        ├─▶ evaluate operational risk    (deterministic rules)
        ├─▶ persist each risk signal
        └─▶ open an escalation if human review is required
```

Because it is one transaction, an escalation can never reference a response that
was not saved, and a stored response can never skip its risk evaluation.

### The rules engine

| Condition | Signal | Level |
|---|---|---|
| missed doses ≥ threshold (default 3) | `MULTIPLE_MISSED_DOSES` | MEDIUM |
| symptoms self-reported | `SYMPTOMS_REPORTED` | HIGH |
| refill needed | `REFILL_NEEDED` | MEDIUM |
| medication not taken | `MEDICATION_NOT_TAKEN` | MEDIUM |
| adherence below threshold (default 80%) | `LOW_ADHERENCE` | MEDIUM |

The result is the highest level among raised signals. **Human review is required
when any HIGH signal fires, or when two or more signals fire together.**

The engine is a pure function of its inputs
([`RiskEvaluationService`](backend/src/main/java/com/careflow/risk/RiskEvaluationService.java)),
so every escalation is reproducible and explainable during an audit.

### Adherence

```
adherencePercentage = takenDoses / expectedDoses × 100
```

Expected doses are projected from the patient's *active* prescriptions and their
dosing frequency across the interval a follow-up covers — so a patient on four
medicines is not judged by the same raw dose count as one on a single tablet.
Percentages are always computed by the backend; clients never supply them. A
patient with no recorded doses reports `null`, not `0%` — "no data" and "zero
adherence" are different facts.

---

## 6. API reference

Interactive docs: **`/swagger-ui.html`** · OpenAPI JSON: **`/v3/api-docs`**

| Method | Endpoint | Roles |
|---|---|---|
| `POST` | `/api/auth/register` | public (ADMIN role requires an admin) |
| `POST` | `/api/auth/login` | public |
| `GET` | `/api/auth/me` | authenticated |
| `GET` | `/api/patients?search=&riskLevel=&page=&size=` | ADMIN, CARE_MANAGER |
| `POST` `GET` `PUT` | `/api/patients`, `/api/patients/{id}` | ADMIN, CARE_MANAGER |
| `DELETE` | `/api/patients/{id}` | ADMIN |
| `GET` `POST` | `/api/patients/{id}/medications` | scoped |
| `PUT` `DELETE` | `/api/medications/{id}` | ADMIN, CARE_MANAGER |
| `GET` `POST` | `/api/patients/{id}/care-plan` | scoped |
| `POST` | `/api/patients/{id}/discharge` | ADMIN, CARE_MANAGER |
| `GET` | `/api/patients/{id}/follow-ups` | scoped |
| `POST` | `/api/follow-ups/{id}/response` | scoped |
| `GET` | `/api/patients/{id}/adherence` | scoped |
| `GET` | `/api/patients/{id}/audit-log` | ADMIN, CARE_MANAGER |
| `GET` | `/api/escalations` | ADMIN, CARE_MANAGER |
| `POST` | `/api/escalations/{id}/assign\|review\|resolve` | ADMIN, CARE_MANAGER |
| `GET` | `/api/dashboard/summary\|follow-ups\|escalations\|adherence\|activity` | ADMIN, CARE_MANAGER |
| `GET` | `/api/system/version` | public |
| `GET` | `/actuator/health` | public |

"Scoped" means care managers reach only their own caseload and patient accounts
only their own record — enforced in the service layer, not just the URL.

Errors use a consistent envelope with a `requestId` that matches the server log
line, so a user-reported failure is traceable:

```json
{
  "timestamp": "2026-08-17T09:12:04Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "A response has already been recorded for follow-up 5.",
  "path": "/api/follow-ups/5/response",
  "requestId": "0f2c9a1e-…"
}
```

---

## 7. Local setup

**Prerequisites:** JDK 21+, Node.js 20+, Docker (for MySQL).

The fastest path is Docker Compose, which starts MySQL, the backend and the
frontend together:

```bash
cp .env.example .env          # then edit the values
cd deployment
docker compose up --build
```

- Frontend → <http://localhost:3000>
- API → <http://localhost:8080>
- Swagger → <http://localhost:8080/swagger-ui.html>

### Running the pieces directly

The backend needs a reachable MySQL and reads **all** database settings from the
environment — there are no hardcoded credentials or hosts:

```bash
cd backend
DB_HOST=localhost DB_PORT=3306 DB_NAME=careflow \
DB_USERNAME=careflow_app DB_PASSWORD=<password> \
JWT_SECRET=<at least 32 bytes> SEED_ENABLED=true \
./mvnw spring-boot:run
```

```bash
cd frontend
npm install
NEXT_PUBLIC_API_URL=http://localhost:8080 npm run dev
```

> The Maven Wrapper (`./mvnw`) is committed, so a separate Maven installation is
> not required.

---

## 8. Environment configuration

Copy [`.env.example`](.env.example) to `.env` — which is gitignored and must
never be committed.

| Variable | Purpose |
|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | Database connection |
| `JWT_SECRET` | HS256 signing key — **must supply ≥ 32 bytes**; the app refuses to start otherwise |
| `JWT_EXPIRATION_MINUTES` | Token lifetime (default 480) |
| `APP_VERSION` `APP_ENV` `GIT_COMMIT` `DEPLOYED_AT` | Reported by `/api/system/version` |
| `CORS_ALLOWED_ORIGINS` | Permitted browser origins |
| `SEED_ENABLED` | Loads fictional demo data. **Keep `false` in production** |
| `ADHERENCE_LOW_THRESHOLD` `RISK_MISSED_DOSE_THRESHOLD` | Tunable rule thresholds |
| `GROQ_ENABLED` `GROQ_API_KEY` `GROQ_MODEL` | Optional case-briefing summaries |
| `NEXT_PUBLIC_API_URL` | API base URL for the frontend |

Generate a signing key with `openssl rand -base64 48`.

### Optional AI briefings

When `GROQ_ENABLED=true` and a key is present, the escalation detail screen shows
a short generated briefing to orient the reviewing care manager. It is **not** in
the decision path: the risk engine stays fully deterministic, the prompt forbids
diagnosis and treatment advice, and any failure or timeout falls back to a
deterministic template. With the flag off, the template is always used.

---

## 9. Docker

Both images are multi-stage and run as a non-root user (uid 1001) with a
container `HEALTHCHECK`.

```bash
docker build -t careflow-backend:1.0.0 \
  --build-arg APP_VERSION=1.0.0 \
  --build-arg GIT_COMMIT=$(git rev-parse --short HEAD) \
  backend/
```

Dependency resolution sits in its own layer, so source-only changes skip the
download on rebuild.

---

## 10. GCE architecture

Provision a VM once:

```bash
export PROJECT_ID=<your-project>
./deployment/gce/setup-gcp.sh                    # APIs, registry, VM, IAM, secrets
gcloud compute ssh careflow-vm --zone us-central1-a --tunnel-through-iap
sudo bash provision-vm.sh careflow.example.com   # Docker, Nginx, UFW, systemd, TLS
```

[`provision-vm.sh`](deployment/gce/provision-vm.sh) installs Docker and Nginx,
creates an unprivileged `careflow` account, configures UFW (deny inbound except
22/80/443), installs the reverse proxy, sets up Certbot, caps container log size,
and registers a systemd unit for restart-on-boot.

---

## 11. Artifact Registry

```
REGION-docker.pkg.dev/PROJECT_ID/careflow-repo/careflow-backend
```

Every build pushes three tags — `:<commit-sha>`, `:<version>` and `:latest` —
but **deployment always uses the immutable commit-SHA tag**, so the VM provably
runs exactly the build that was tested, and the previous SHA remains available
for rollback.

---

## 12. Cloud Build CI/CD

[`cloudbuild.yaml`](cloudbuild.yaml) runs on every push to `main`:

```
test ─▶ package ─▶ docker build ─▶ push ─▶ deploy ─▶ verify
```

The pipeline **fails closed**:

- `mvn clean test` runs first; any failing test stops the build before an image exists.
- `deploy.sh` gates on the application's own health endpoint and rolls back on failure.
- A final `verify` step independently confirms `/api/system/version` reports the
  expected commit SHA — proving the new build is actually serving traffic, not
  just that a container started.

Connect the trigger:

```bash
gcloud builds triggers create github \
  --name=careflow-main \
  --repo-owner=<owner> --repo-name=<repo> \
  --branch-pattern='^main$' \
  --build-config=cloudbuild.yaml
```

---

## 13. Deployment and rollback

[`deploy.sh`](deployment/gce/deploy.sh) implements a health-gated swap:

```
record current image ─▶ pull new ─▶ start new ─▶ health check
                                                     │
                                    healthy ─────────┴───────── unhealthy
                                       │                            │
                                  keep new                  restart previous image
                                                            and exit non-zero
```

If the new container fails to report `UP` within 120 s — or exits during startup —
the previous image is restarted automatically and the script exits non-zero,
turning the build red. A failed deploy therefore never leaves the VM serving a
broken build.

**Manual rollback:**

```bash
sudo /opt/careflow/deploy.sh REGION-docker.pkg.dev/PROJECT/careflow-repo/careflow-backend:<previous-sha>
```

---

## 14. Security

| Concern | Control |
|---|---|
| Passwords | BCrypt (strength 12); plaintext is never stored or logged |
| Tokens | HS256 JWT; a secret under 32 bytes fails startup rather than weakening signing |
| Privilege escalation | `POST /api/auth/register` refuses to create an ADMIN unless an admin is authenticated |
| Data scoping | Care managers reach only their caseload; patients only their own record — enforced in the service layer |
| Transport | TLS at Nginx, HSTS, `X-Frame-Options: DENY`, `nosniff` |
| Application exposure | Spring Boot binds to `127.0.0.1` only; never published |
| Brute force | Nginx rate-limits `/api/auth/` to 10 req/min per IP |
| Secrets | Google Secret Manager; never in source, images or `cloudbuild.yaml` |
| SSH | No private key in CI — Cloud Build uses IAP-tunnelled SSH; port 22 restricted to `35.235.240.0/20` |
| Containers | Non-root user, pinned base images, no build secrets in layers |
| Logging | Structured logs with a request id; tokens, passwords and credentials are never logged |
| Audit | Append-only trail; audit rows commit in the same transaction as the action |

`/actuator` beyond the health probe is blocked at Nginx *and* restricted to
ADMIN in Spring Security.

### Required IAM roles

**Cloud Build service account** — `artifactregistry.writer`,
`compute.instanceAdmin.v1`, `iap.tunnelResourceAccessor`, `iam.serviceAccountUser`,
`secretmanager.secretAccessor`.

**VM service account** — `artifactregistry.reader`, `secretmanager.secretAccessor`.

---

## 15. Testing

```bash
cd backend && ./mvnw test
```

**73 tests, all passing.**

| Suite | Covers |
|---|---|
| `RiskEvaluationServiceTest` (14) | Every rule, thresholds, combinations, determinism, non-diagnostic phrasing |
| `AdherenceServiceTest` (7) | Percentage maths, aggregation, empty history, dose projection, capping |
| `FollowUpSchedulingServiceTest` (5) | STANDARD 1/7/14/30, HIGH_RISK adds day 3, task linkage |
| `EscalationServiceTest` (9) | Severity mapping, deduplication, assignment, resolution, invalid transitions |
| `JwtTokenProviderTest` (7) | Raw vs base64 secrets, short-secret rejection, claims, tampering |
| `AuthenticationIntegrationTest` (11) | Login, hashing, duplicates, weak passwords, admin self-registration |
| `AuthorizationIntegrationTest` (10) | Caseload isolation, role boundaries, public endpoints |
| `CareWorkflowIntegrationTest` (10) | Discharge → response → risk → escalation → resolution, plus the audit trail |

Integration tests boot the real application context and exercise the full
security filter chain. They run against H2 in MySQL-compatibility mode so CI
needs no database container; the Flyway MySQL migration is exercised against the
real engine via Docker Compose.

---

## 16. Demo walkthrough

Start with `SEED_ENABLED=true` for ten fictional patients with medications, care
plans, follow-ups, adherence history and escalations.

**Demo credentials** (development seed only):

| Role | Email | Password |
|---|---|---|
| Admin | `admin@careflow.health` | `CareFlow!2026` |
| Care manager | `alex.chen@careflow.health` | `CareFlow!2026` |
| Patient | `rina.mehta@example.com` | `CareFlow!2026` |

1. **Sign in** as the care manager → the dashboard shows patients, adherence, today's follow-ups and open escalations.
2. **Open a patient** (Rina Mehta) → care journey, medications, adherence chart, audit trail.
3. **Record a follow-up response**: 3 missed doses, symptoms *yes*, refill *yes*.
4. **Watch the chain fire** — the response returns the risk evaluation (`HIGH`,
   signals `MULTIPLE_MISSED_DOSES` + `SYMPTOMS_REPORTED`), recalculated adherence
   and the newly opened escalation.
5. **Open Escalations** → the new case is at the top.
6. **Assign** it, **mark in review**, then **resolve** with notes (required).
7. **Re-read the audit trail** → every step recorded with actor and timestamp.

### CI/CD demonstration

Push to `main`, watch Cloud Build run tests → build → push → deploy → health
check. Then bump `_APP_VERSION` to `1.1.0`, push again, and refresh `/system` —
the version and commit change, proving the pipeline deployed the new build.

---

## 17. Project layout

```
careflow/
├── backend/                 Spring Boot API
│   ├── src/main/java/com/careflow/
│   │   ├── auth/  patient/  medication/  careplan/
│   │   ├── followup/  adherence/  risk/  escalation/
│   │   ├── audit/  dashboard/  system/  ai/
│   │   ├── common/  config/  exception/  security/
│   │   └── CareFlowApplication.java
│   ├── src/main/resources/db/migration/
│   ├── src/test/java/com/careflow/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                Next.js dashboard
│   ├── src/app/             login, dashboard, patients, escalations, system, portal
│   ├── src/components/      layout, charts, patient, ui, providers
│   ├── src/services/        API client and typed endpoints
│   ├── src/types/           DTO contracts
│   └── Dockerfile
├── deployment/
│   ├── nginx/               reverse proxy + shared proxy headers
│   ├── gce/                 setup-gcp.sh, provision-vm.sh, deploy.sh
│   └── docker-compose.yml
├── architecture/
├── cloudbuild.yaml
└── .env.example
```
