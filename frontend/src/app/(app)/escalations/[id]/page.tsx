'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useAuth } from '@/components/providers/AuthProvider';
import { useToast } from '@/components/providers/ToastProvider';
import { EscalationStatusBadge, RiskBadge, SeverityBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { ApiRequestError } from '@/services/api-client';
import { escalationApi, patientApi } from '@/services/careflow-api';

function formatDateTime(value?: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function EscalationDetailPage() {
  const params = useParams<{ id: string }>();
  const escalationId = Number(params.id);
  const { user } = useAuth();
  const toast = useToast();

  const [resolutionNotes, setResolutionNotes] = useState('');
  const [working, setWorking] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const escalation = useApiResource(() => escalationApi.getById(escalationId), [escalationId]);
  const summary = useApiResource(() => escalationApi.summary(escalationId), [escalationId]);

  const patientId = escalation.data?.patientId;
  const medications = useApiResource(
    () => (patientId ? patientApi.medications(patientId) : Promise.resolve([])),
    [patientId],
  );
  const adherence = useApiResource(
    () => (patientId ? patientApi.adherence(patientId) : Promise.resolve(null)),
    [patientId],
  );
  const auditLog = useApiResource(
    () => (patientId ? patientApi.auditLog(patientId, 0, 15) : Promise.resolve(null)),
    [patientId],
  );

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setWorking(true);
    setActionError(null);
    try {
      await action();
      toast.success(successMessage);
      escalation.reload();
      auditLog.reload();
    } catch (cause) {
      const message =
        cause instanceof ApiRequestError ? cause.message : 'This action could not be completed.';
      setActionError(message);
      toast.error('Action failed', message);
    } finally {
      setWorking(false);
    }
  }

  if (escalation.loading) {
    return (
      <div className="mx-auto max-w-7xl">
        <LoadingSkeleton rows={8} />
      </div>
    );
  }

  if (escalation.error || !escalation.data) {
    return (
      <div className="mx-auto max-w-7xl">
        <div className="card">
          <ErrorState
            message={escalation.error ?? 'This escalation could not be loaded.'}
            onRetry={escalation.reload}
          />
        </div>
      </div>
    );
  }

  const record = escalation.data;
  const resolved = record.status === 'RESOLVED';
  const notesTooShort = resolutionNotes.trim().length < 10;

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <nav aria-label="Breadcrumb">
        <Link href="/escalations" className="text-sm text-ink-muted hover:text-ink">
          ← Back to escalations
        </Link>
      </nav>

      <header className="card-padded">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-2xl font-semibold tracking-tight text-ink">
                {record.patientName}
              </h1>
              <span className="font-mono text-xs text-ink-subtle">
                {record.medicalRecordNumber}
              </span>
            </div>
            <p className="mt-2 max-w-2xl text-sm text-ink-muted">{record.reason}</p>
            <p className="mt-2 text-xs text-ink-subtle">
              Opened {formatDateTime(record.createdAt)}
              {record.assignedCareManagerName && ` · assigned to ${record.assignedCareManagerName}`}
              {record.resolvedAt && ` · resolved ${formatDateTime(record.resolvedAt)}`}
            </p>
          </div>
          <div className="flex flex-col items-end gap-2">
            <SeverityBadge severity={record.severity} />
            <EscalationStatusBadge status={record.status} />
          </div>
        </div>

        <p className="mt-4 rounded-lg bg-surface-muted px-3.5 py-3 text-xs leading-relaxed text-ink-muted">
          CareFlow flags operational signals only. It does not diagnose conditions or recommend
          treatment — every care decision on this case is yours.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <section className="card">
            <header className="border-b border-border px-5 py-4">
              <h2 className="text-sm font-semibold text-ink">Case briefing</h2>
            </header>
            <div className="p-5">
              {summary.loading ? (
                <LoadingSkeleton rows={2} />
              ) : summary.data ? (
                <>
                  <p className="whitespace-pre-line text-sm leading-relaxed text-ink">
                    {summary.data.summary}
                  </p>
                  <p className="mt-3 text-xs text-ink-subtle">
                    Source: {summary.data.source === 'template' ? 'deterministic template' : summary.data.source}
                  </p>
                </>
              ) : (
                <EmptyState title="No briefing available" />
              )}
            </div>
          </section>

          <section className="card">
            <header className="border-b border-border px-5 py-4">
              <h2 className="text-sm font-semibold text-ink">Medication context</h2>
            </header>
            {medications.loading ? (
              <div className="p-5">
                <LoadingSkeleton rows={2} />
              </div>
            ) : medications.data && medications.data.length > 0 ? (
              <ul className="divide-y divide-border">
                {medications.data
                  .filter((medication) => medication.active)
                  .map((medication) => (
                    <li key={medication.id} className="flex items-center justify-between px-5 py-3">
                      <div>
                        <p className="text-sm font-medium text-ink">{medication.medicineName}</p>
                        <p className="text-xs text-ink-muted">
                          {medication.dosage} · {medication.frequencyLabel}
                        </p>
                      </div>
                      {medication.instructions && (
                        <p className="max-w-xs text-right text-xs text-ink-subtle">
                          {medication.instructions}
                        </p>
                      )}
                    </li>
                  ))}
              </ul>
            ) : (
              <EmptyState title="No active medications" />
            )}
          </section>

          <section className="card">
            <header className="border-b border-border px-5 py-4">
              <h2 className="text-sm font-semibold text-ink">Audit history</h2>
            </header>
            <div className="p-2">
              {auditLog.loading ? (
                <div className="p-3">
                  <LoadingSkeleton rows={4} />
                </div>
              ) : auditLog.data && auditLog.data.content.length > 0 ? (
                <ul className="divide-y divide-border">
                  {auditLog.data.content.map((event) => (
                    <li key={event.id} className="px-3 py-2.5">
                      <p className="text-sm text-ink">{event.description}</p>
                      <p className="mt-0.5 text-xs text-ink-subtle">
                        <span className="font-mono">{event.action}</span> ·{' '}
                        {event.actorName ?? 'System'} · {formatDateTime(event.timestamp)}
                      </p>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState title="No audit history" />
              )}
            </div>
          </section>
        </div>

        <div className="space-y-6">
          <section className="card-padded">
            <h2 className="text-sm font-semibold text-ink">Patient snapshot</h2>
            <dl className="mt-4 space-y-3 text-sm">
              <div className="flex items-center justify-between">
                <dt className="text-ink-muted">Adherence</dt>
                <dd className="font-medium text-ink tabular">
                  {adherence.data ? `${adherence.data.adherencePercentage.toFixed(1)}%` : '—'}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-ink-muted">Missed doses</dt>
                <dd className="font-medium text-ink tabular">
                  {adherence.data?.missedDoses ?? '—'}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-ink-muted">Current risk</dt>
                <dd>
                  <RiskBadge
                    level={
                      record.severity === 'HIGH' || record.severity === 'CRITICAL'
                        ? 'HIGH'
                        : record.severity === 'MEDIUM'
                          ? 'MEDIUM'
                          : 'LOW'
                    }
                  />
                </dd>
              </div>
            </dl>
            <Link
              href={`/patients/${record.patientId}`}
              className="btn-secondary mt-4 w-full"
            >
              Open patient record
            </Link>
          </section>

          <section className="card-padded">
            <h2 className="text-sm font-semibold text-ink">Actions</h2>

            {resolved ? (
              <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 p-4">
                <p className="text-sm font-medium text-emerald-900">Case resolved</p>
                <p className="mt-1.5 text-sm text-emerald-800">{record.resolutionNotes}</p>
                <p className="mt-2 text-xs text-emerald-700">
                  {record.resolvedByName} · {formatDateTime(record.resolvedAt)}
                </p>
              </div>
            ) : (
              <div className="mt-4 space-y-3">
                {!record.assignedCareManagerId && user && (
                  <button
                    type="button"
                    disabled={working}
                    onClick={() =>
                      runAction(
                        () => escalationApi.assign(record.id, user.id),
                        'Escalation assigned to you.',
                      )
                    }
                    className="btn-secondary w-full"
                  >
                    Assign to me
                  </button>
                )}

                {record.status === 'ASSIGNED' && (
                  <button
                    type="button"
                    disabled={working}
                    onClick={() =>
                      runAction(() => escalationApi.markInReview(record.id), 'Marked as in review.')
                    }
                    className="btn-secondary w-full"
                  >
                    Mark in review
                  </button>
                )}

                <div className="border-t border-border pt-3">
                  <label htmlFor="resolution" className="label mb-1.5">
                    Resolution notes
                  </label>
                  <textarea
                    id="resolution"
                    rows={4}
                    maxLength={2000}
                    value={resolutionNotes}
                    onChange={(event) => setResolutionNotes(event.target.value)}
                    placeholder="What was done to resolve this case?"
                    className="input resize-none"
                    aria-describedby="resolution-hint"
                  />
                  <p id="resolution-hint" className="mt-1 text-xs text-ink-subtle">
                    Required, minimum 10 characters. Recorded in the audit trail.
                  </p>
                  <button
                    type="button"
                    disabled={working || notesTooShort}
                    onClick={() =>
                      runAction(
                        () => escalationApi.resolve(record.id, resolutionNotes.trim()),
                        'Escalation resolved.',
                      )
                    }
                    className="btn-primary mt-3 w-full"
                  >
                    {working ? 'Working…' : 'Resolve case'}
                  </button>
                </div>

                {actionError && (
                  <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-800">
                    {actionError}
                  </div>
                )}
              </div>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}
