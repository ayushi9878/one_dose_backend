'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { EscalationStatusBadge, SeverityBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { escalationApi } from '@/services/careflow-api';
import type { EscalationSeverity, EscalationStatus } from '@/types/api';

type Filter =
  | { kind: 'ALL' }
  | { kind: 'SEVERITY'; value: EscalationSeverity }
  | { kind: 'STATUS'; value: EscalationStatus };

const FILTERS: Array<{ label: string; filter: Filter }> = [
  { label: 'All', filter: { kind: 'ALL' } },
  { label: 'High', filter: { kind: 'SEVERITY', value: 'HIGH' } },
  { label: 'Medium', filter: { kind: 'SEVERITY', value: 'MEDIUM' } },
  { label: 'Pending', filter: { kind: 'STATUS', value: 'PENDING' } },
  { label: 'Assigned', filter: { kind: 'STATUS', value: 'ASSIGNED' } },
  { label: 'Resolved', filter: { kind: 'STATUS', value: 'RESOLVED' } },
];

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function EscalationsPage() {
  const [activeLabel, setActiveLabel] = useState('All');
  const [page, setPage] = useState(0);

  // Derived inside the memo: building the filter object in the render body
  // would produce a new reference every pass and defeat memoisation entirely.
  const query = useMemo(() => {
    const active: Filter =
      FILTERS.find((entry) => entry.label === activeLabel)?.filter ?? { kind: 'ALL' };
    return {
      severity: active.kind === 'SEVERITY' ? active.value : undefined,
      status: active.kind === 'STATUS' ? active.value : undefined,
      page,
      size: 20,
    };
  }, [activeLabel, page]);

  const escalations = useApiResource(
    () => escalationApi.search(query),
    [query.severity, query.status, query.page],
  );

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-display font-semibold text-ink">Escalations</h1>
          <p className="mt-1 text-sm text-ink-muted">
            Cases routed to a care manager for human review.
          </p>
        </div>
        {escalations.data && (
          <p className="text-sm text-ink-muted tabular">
            {escalations.data.totalElements.toLocaleString()}{' '}
            {escalations.data.totalElements === 1 ? 'case' : 'cases'}
          </p>
        )}
      </header>

      <div className="card">
        <div className="flex flex-wrap gap-1 border-b border-border p-4" role="group" aria-label="Filter escalations">
          {FILTERS.map((entry) => (
            <button
              key={entry.label}
              type="button"
              onClick={() => {
                setActiveLabel(entry.label);
                setPage(0);
              }}
              aria-pressed={activeLabel === entry.label}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors
                          ${
                            activeLabel === entry.label
                              ? 'bg-brand-600 text-white'
                              : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
                          }`}
            >
              {entry.label}
            </button>
          ))}
        </div>

        {escalations.loading ? (
          <div className="p-5">
            <LoadingSkeleton rows={5} />
          </div>
        ) : escalations.error ? (
          <ErrorState message={escalations.error} onRetry={escalations.reload} />
        ) : escalations.data && escalations.data.content.length > 0 ? (
          <>
            <ul className="divide-y divide-border">
              {escalations.data.content.map((escalation) => (
                <li key={escalation.id}>
                  <Link
                    href={`/escalations/${escalation.id}`}
                    className="block px-5 py-4 transition-colors hover:bg-surface-muted"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-medium text-ink">{escalation.patientName}</p>
                          <span className="font-mono text-xs text-ink-subtle">
                            {escalation.medicalRecordNumber}
                          </span>
                        </div>
                        <p className="mt-1 text-sm text-ink-muted">{escalation.reason}</p>
                        <p className="mt-2 text-xs text-ink-subtle">
                          Opened {formatDateTime(escalation.createdAt)}
                          {escalation.assignedCareManagerName &&
                            ` · assigned to ${escalation.assignedCareManagerName}`}
                        </p>
                      </div>
                      <div className="flex shrink-0 flex-col items-end gap-2">
                        <SeverityBadge severity={escalation.severity} />
                        <EscalationStatusBadge status={escalation.status} />
                      </div>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>

            {escalations.data.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-border px-4 py-3">
                <p className="text-sm text-ink-muted">
                  Page {escalations.data.page + 1} of {escalations.data.totalPages}
                </p>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setPage((current) => Math.max(0, current - 1))}
                    disabled={escalations.data.first}
                    className="btn-secondary"
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage((current) => current + 1)}
                    disabled={escalations.data.last}
                    className="btn-secondary"
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </>
        ) : (
          <EmptyState
            title="No escalations match this filter"
            description="Cases appear here when the rules engine flags a response for review."
          />
        )}
      </div>
    </div>
  );
}
