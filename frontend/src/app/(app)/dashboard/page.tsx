'use client';

import Link from 'next/link';
import { AdherenceDistributionChart } from '@/components/charts/AdherenceDistributionChart';
import { EscalationStatusBadge, FollowUpBadge, SeverityBadge } from '@/components/ui/Badge';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { StatCard } from '@/components/ui/StatCard';
import { useApiResource } from '@/hooks/useApiResource';
import { dashboardApi } from '@/services/careflow-api';

function formatRelative(timestamp: string): string {
  const then = new Date(timestamp).getTime();
  const minutes = Math.round((Date.now() - then) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export default function DashboardPage() {
  const summary = useApiResource(() => dashboardApi.summary(), []);
  const followUps = useApiResource(() => dashboardApi.followUps(undefined, 6), []);
  const escalations = useApiResource(() => dashboardApi.escalations(5), []);
  const adherence = useApiResource(() => dashboardApi.adherence(), []);
  const activity = useApiResource(() => dashboardApi.activity(), []);

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <header>
        <h1 className="text-display font-semibold text-ink">Dashboard</h1>
        <p className="mt-1 text-sm text-ink-muted">
          Continuity care across your patient population.
        </p>
      </header>

      {summary.loading ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="card-padded">
              <div className="skeleton h-4 w-24" />
              <div className="skeleton mt-3 h-8 w-16" />
            </div>
          ))}
        </div>
      ) : summary.error ? (
        <div className="card">
          <ErrorState message={summary.error} onRetry={summary.reload} />
        </div>
      ) : summary.data ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
          <StatCard
            label="Total patients"
            value={summary.data.totalPatients.toLocaleString()}
            hint={`${summary.data.highRiskPatients} at high risk`}
            href="/patients"
          />
          <StatCard
            label="Active care plans"
            value={summary.data.activeCarePlans.toLocaleString()}
          />
          <StatCard
            label="Today's follow-ups"
            value={summary.data.todayFollowUps.toLocaleString()}
            hint={
              summary.data.overdueFollowUps > 0
                ? `${summary.data.overdueFollowUps} overdue`
                : 'None overdue'
            }
            tone={summary.data.overdueFollowUps > 0 ? 'warning' : 'default'}
          />
          <StatCard
            label="Overall adherence"
            value={`${summary.data.adherenceRate.toFixed(1)}%`}
          />
          <StatCard
            label="Open escalations"
            value={summary.data.pendingEscalations.toLocaleString()}
            hint="Awaiting human review"
            href="/escalations"
            tone={summary.data.pendingEscalations > 0 ? 'critical' : 'default'}
          />
        </div>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card lg:col-span-2">
          <header className="flex items-center justify-between border-b border-border px-5 py-4">
            <div>
              <h2 className="text-sm font-semibold text-ink">Adherence distribution</h2>
              <p className="mt-0.5 text-xs text-ink-muted">
                Patients grouped by calculated adherence.
              </p>
            </div>
          </header>
          <div className="p-5">
            {adherence.loading ? (
              <LoadingSkeleton rows={4} />
            ) : adherence.error ? (
              <ErrorState message={adherence.error} onRetry={adherence.reload} />
            ) : adherence.data && adherence.data.distribution.some((b) => b.patientCount > 0) ? (
              <AdherenceDistributionChart
                distribution={adherence.data.distribution}
                lowThreshold={adherence.data.lowThresholdPercentage}
              />
            ) : (
              <EmptyState
                title="No adherence data yet"
                description="Adherence appears once patients begin responding to follow-ups."
              />
            )}
          </div>
        </section>

        <section className="card">
          <header className="flex items-center justify-between border-b border-border px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">Escalation queue</h2>
            <Link href="/escalations" className="text-xs font-medium text-brand-600 hover:text-brand-700">
              View all
            </Link>
          </header>
          <div className="p-2">
            {escalations.loading ? (
              <div className="p-3">
                <LoadingSkeleton rows={3} />
              </div>
            ) : escalations.error ? (
              <ErrorState message={escalations.error} onRetry={escalations.reload} />
            ) : escalations.data && escalations.data.content.length > 0 ? (
              <ul className="divide-y divide-border">
                {escalations.data.content.map((escalation) => (
                  <li key={escalation.id}>
                    <Link
                      href={`/escalations/${escalation.id}`}
                      className="block rounded-lg px-3 py-3 transition-colors hover:bg-surface-muted"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <p className="truncate text-sm font-medium text-ink">
                          {escalation.patientName}
                        </p>
                        <SeverityBadge severity={escalation.severity} />
                      </div>
                      <p className="mt-1 line-clamp-2 text-xs text-ink-muted">
                        {escalation.reason}
                      </p>
                      <div className="mt-2 flex items-center gap-2">
                        <EscalationStatusBadge status={escalation.status} />
                        <span className="text-xs text-ink-subtle">
                          {formatRelative(escalation.createdAt)}
                        </span>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                title="No open escalations"
                description="Cases needing human review will appear here."
              />
            )}
          </div>
        </section>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="card">
          <header className="border-b border-border px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">Upcoming follow-ups</h2>
          </header>
          <div className="p-2">
            {followUps.loading ? (
              <div className="p-3">
                <LoadingSkeleton rows={3} />
              </div>
            ) : followUps.error ? (
              <ErrorState message={followUps.error} onRetry={followUps.reload} />
            ) : followUps.data && followUps.data.content.length > 0 ? (
              <ul className="divide-y divide-border">
                {followUps.data.content.map((followUp) => (
                  <li key={followUp.id}>
                    <Link
                      href={`/patients/${followUp.patientId}`}
                      className="flex items-center justify-between gap-3 rounded-lg px-3 py-3 transition-colors hover:bg-surface-muted"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-ink">
                          {followUp.patientName}
                        </p>
                        <p className="mt-0.5 text-xs text-ink-muted">
                          {followUp.title ?? 'Follow-up'} · {formatDate(followUp.scheduledDate)}
                        </p>
                      </div>
                      <FollowUpBadge status={followUp.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState title="No upcoming follow-ups" />
            )}
          </div>
        </section>

        <section className="card">
          <header className="border-b border-border px-5 py-4">
            <h2 className="text-sm font-semibold text-ink">Recent activity</h2>
          </header>
          <div className="p-2">
            {activity.loading ? (
              <div className="p-3">
                <LoadingSkeleton rows={4} />
              </div>
            ) : activity.error ? (
              <ErrorState message={activity.error} onRetry={activity.reload} />
            ) : activity.data && activity.data.length > 0 ? (
              <ul className="divide-y divide-border">
                {activity.data.slice(0, 8).map((event) => (
                  <li key={event.id} className="px-3 py-2.5">
                    <p className="text-sm text-ink">{event.description}</p>
                    <p className="mt-0.5 text-xs text-ink-subtle">
                      {event.actorName ?? 'System'} · {formatRelative(event.timestamp)}
                    </p>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState title="No activity yet" />
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
