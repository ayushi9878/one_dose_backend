'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { AdherenceTrendChart } from '@/components/charts/AdherenceTrendChart';
import { CareJourney } from '@/components/patient/CareJourney';
import { FollowUpResponseForm } from '@/components/patient/FollowUpResponseForm';
import { RiskBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { ApiRequestError } from '@/services/api-client';
import { patientApi } from '@/services/careflow-api';
import type { FollowUp } from '@/types/api';

function formatDate(value?: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function PatientDetailPage() {
  const params = useParams<{ id: string }>();
  const patientId = Number(params.id);
  const [activeFollowUp, setActiveFollowUp] = useState<FollowUp | null>(null);

  const patient = useApiResource(() => patientApi.getById(patientId), [patientId]);
  const medications = useApiResource(() => patientApi.medications(patientId), [patientId]);
  const followUps = useApiResource(() => patientApi.followUps(patientId), [patientId]);
  const adherence = useApiResource(() => patientApi.adherence(patientId), [patientId]);
  const auditLog = useApiResource(() => patientApi.auditLog(patientId), [patientId]);

  // A patient may have no care plan yet; that is an expected state, not an error.
  const carePlan = useApiResource(
    () =>
      patientApi.carePlan(patientId).catch((cause: unknown) => {
        if (cause instanceof ApiRequestError && cause.status === 404) return null;
        throw cause;
      }),
    [patientId],
  );

  function refreshAfterResponse() {
    setActiveFollowUp(null);
    followUps.reload();
    adherence.reload();
    auditLog.reload();
    patient.reload();
  }

  if (patient.loading) {
    return (
      <div className="mx-auto max-w-7xl">
        <LoadingSkeleton rows={8} />
      </div>
    );
  }

  if (patient.error || !patient.data) {
    return (
      <div className="mx-auto max-w-7xl">
        <div className="card">
          <ErrorState
            message={patient.error ?? 'This patient could not be loaded.'}
            onRetry={patient.reload}
          />
        </div>
      </div>
    );
  }

  const record = patient.data;
  const nextFollowUp = followUps.data?.find(
    (followUp) => followUp.status === 'SCHEDULED' || followUp.status === 'PENDING',
  );

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <nav aria-label="Breadcrumb">
        <Link href="/patients" className="text-sm text-ink-muted hover:text-ink">
          ← Back to patients
        </Link>
      </nav>

      <header className="card-padded">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-100 text-lg font-semibold text-brand-700">
              {record.firstName.charAt(0)}
              {record.lastName.charAt(0)}
            </div>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight text-ink">{record.fullName}</h1>
              <dl className="mt-2 flex flex-wrap gap-x-6 gap-y-1 text-sm">
                <div className="flex gap-1.5">
                  <dt className="text-ink-subtle">Record</dt>
                  <dd className="font-mono text-xs leading-5 text-ink-muted">
                    {record.medicalRecordNumber}
                  </dd>
                </div>
                <div className="flex gap-1.5">
                  <dt className="text-ink-subtle">Age</dt>
                  <dd className="text-ink-muted tabular">{record.age}</dd>
                </div>
                <div className="flex gap-1.5">
                  <dt className="text-ink-subtle">Condition</dt>
                  <dd className="text-ink-muted">{record.primaryCondition ?? '—'}</dd>
                </div>
                <div className="flex gap-1.5">
                  <dt className="text-ink-subtle">Care manager</dt>
                  <dd className="text-ink-muted">{record.careManagerName ?? 'Unassigned'}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div className="flex flex-col items-end gap-2">
            <RiskBadge level={record.currentRiskLevel} />
            <p className="text-xs text-ink-subtle">
              {record.dischargeDate
                ? `Discharged ${formatDate(record.dischargeDate)}`
                : 'Not yet discharged'}
            </p>
          </div>
        </div>
      </header>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Adherence</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink">
            {adherence.data ? `${adherence.data.adherencePercentage.toFixed(1)}%` : '—'}
          </p>
          {adherence.data && (
            <p className="mt-1.5 text-xs text-ink-subtle tabular">
              {adherence.data.takenDoses} of {adherence.data.expectedDoses} doses
            </p>
          )}
        </div>
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Missed doses</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink tabular">
            {adherence.data?.missedDoses ?? '—'}
          </p>
        </div>
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Care plan</p>
          <p className="mt-2 text-lg font-semibold tracking-tight text-ink">
            {carePlan.data ? carePlan.data.planType.replace('_', ' ') : 'None'}
          </p>
          {carePlan.data && (
            <p className="mt-1.5 text-xs text-ink-subtle">
              {carePlan.data.status.toLowerCase()}
            </p>
          )}
        </div>
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Next follow-up</p>
          <p className="mt-2 text-lg font-semibold tracking-tight text-ink">
            {nextFollowUp ? formatDate(nextFollowUp.scheduledDate) : 'None scheduled'}
          </p>
          {nextFollowUp && (
            <button
              type="button"
              onClick={() => setActiveFollowUp(nextFollowUp)}
              className="mt-1.5 text-xs font-medium text-brand-600 hover:text-brand-700"
            >
              Record response
            </button>
          )}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card lg:col-span-1">
          <header className="border-b border-border px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">Care journey</h2>
            <p className="mt-0.5 text-xs text-ink-muted">Post-discharge touchpoints.</p>
          </header>
          <div className="p-5">
            {followUps.loading ? (
              <LoadingSkeleton rows={4} />
            ) : followUps.error ? (
              <ErrorState message={followUps.error} onRetry={followUps.reload} />
            ) : followUps.data && followUps.data.length > 0 ? (
              <CareJourney
                dischargeDate={record.dischargeDate}
                followUps={followUps.data}
                onSelect={setActiveFollowUp}
              />
            ) : (
              <EmptyState
                title="No follow-ups scheduled"
                description="Follow-ups are generated automatically when the patient is discharged."
              />
            )}
          </div>
        </section>

        <section className="card lg:col-span-2">
          <header className="border-b border-border px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">Adherence over time</h2>
            <p className="mt-0.5 text-xs text-ink-muted">
              Calculated by the backend from recorded doses.
            </p>
          </header>
          <div className="p-5">
            {adherence.loading ? (
              <LoadingSkeleton rows={4} />
            ) : adherence.error ? (
              <ErrorState message={adherence.error} onRetry={adherence.reload} />
            ) : adherence.data && adherence.data.history.length > 0 ? (
              <AdherenceTrendChart history={adherence.data.history} />
            ) : (
              <EmptyState
                title="No adherence readings yet"
                description="Readings appear once the patient answers a follow-up."
              />
            )}
          </div>
        </section>
      </div>

      <section className="card">
        <header className="border-b border-border px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Medications</h2>
        </header>
        {medications.loading ? (
          <div className="p-5">
            <LoadingSkeleton rows={3} />
          </div>
        ) : medications.error ? (
          <ErrorState message={medications.error} onRetry={medications.reload} />
        ) : medications.data && medications.data.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="border-b border-border bg-surface-muted">
                <tr>
                  <th scope="col" className="table-header">Medicine</th>
                  <th scope="col" className="table-header">Dosage</th>
                  <th scope="col" className="table-header">Frequency</th>
                  <th scope="col" className="table-header">Started</th>
                  <th scope="col" className="table-header">Instructions</th>
                  <th scope="col" className="table-header">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {medications.data.map((medication) => (
                  <tr key={medication.id}>
                    <td className="table-cell font-medium">{medication.medicineName}</td>
                    <td className="table-cell text-ink-muted">{medication.dosage}</td>
                    <td className="table-cell text-ink-muted">{medication.frequencyLabel}</td>
                    <td className="table-cell text-ink-muted tabular">
                      {formatDate(medication.startDate)}
                    </td>
                    <td className="table-cell text-ink-muted">
                      {medication.instructions ?? '—'}
                    </td>
                    <td className="table-cell">
                      <span
                        className={`badge ${
                          medication.active
                            ? 'bg-emerald-50 text-emerald-800'
                            : 'bg-slate-100 text-slate-700'
                        }`}
                      >
                        {medication.active ? 'Active' : 'Discontinued'}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="No medications recorded" />
        )}
      </section>

      <section className="card">
        <header className="border-b border-border px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Audit trail</h2>
          <p className="mt-0.5 text-xs text-ink-muted">
            Every workflow action recorded for this patient.
          </p>
        </header>
        <div className="p-2">
          {auditLog.loading ? (
            <div className="p-3">
              <LoadingSkeleton rows={5} />
            </div>
          ) : auditLog.error ? (
            <ErrorState message={auditLog.error} onRetry={auditLog.reload} />
          ) : auditLog.data && auditLog.data.content.length > 0 ? (
            <ul className="divide-y divide-border">
              {auditLog.data.content.map((event) => (
                <li key={event.id} className="flex gap-4 px-3 py-3">
                  <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-border-strong" aria-hidden="true" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm text-ink">{event.description}</p>
                    <p className="mt-0.5 text-xs text-ink-subtle">
                      <span className="font-mono">{event.action}</span> ·{' '}
                      {event.actorName ?? 'System'} · {formatDateTime(event.timestamp)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <EmptyState title="No audit events yet" />
          )}
        </div>
      </section>

      {activeFollowUp && (
        <FollowUpResponseForm
          followUp={activeFollowUp}
          onClose={() => setActiveFollowUp(null)}
          onSubmitted={refreshAfterResponse}
        />
      )}
    </div>
  );
}
