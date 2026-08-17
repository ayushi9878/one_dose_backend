'use client';

import { ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { apiBaseUrl } from '@/services/api-client';
import { systemApi } from '@/services/careflow-api';

const PIPELINE_STAGES = [
  { name: 'Unit & integration tests', detail: 'mvn clean test — the build stops here on any failure.' },
  { name: 'Maven build', detail: 'Packages the Spring Boot application.' },
  { name: 'Docker build', detail: 'Multi-stage image, running as a non-root user.' },
  { name: 'Artifact Registry', detail: 'Pushed with commit-SHA, version and latest tags.' },
  { name: 'GCE deployment', detail: 'Deploys the immutable commit-SHA tag over IAP SSH.' },
  { name: 'Health check', detail: 'Rolls back automatically if the new build is unhealthy.' },
];

function StatusDot({ healthy }: { healthy: boolean }) {
  return (
    <span
      className={`inline-block h-2 w-2 rounded-full ${healthy ? 'bg-emerald-600' : 'bg-red-600'}`}
      aria-hidden="true"
    />
  );
}

export default function SystemPage() {
  const version = useApiResource(() => systemApi.version(), []);
  const health = useApiResource(
    () => systemApi.health().catch(() => ({ status: 'DOWN' })),
    [],
  );

  const apiHealthy = !version.error && Boolean(version.data);
  const dbHealthy = health.data?.status === 'UP';

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <h1 className="text-display font-semibold text-ink">System</h1>
        <p className="mt-1 text-sm text-ink-muted">
          Build metadata and deployment health for the running environment.
        </p>
      </header>

      <section className="card">
        <header className="border-b border-border px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Service status</h2>
        </header>
        {version.loading ? (
          <div className="p-5">
            <LoadingSkeleton rows={3} />
          </div>
        ) : (
          <dl className="divide-y divide-border">
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">API</dt>
              <dd className="flex items-center gap-2 text-sm font-medium text-ink">
                <StatusDot healthy={apiHealthy} />
                {apiHealthy ? 'Healthy' : 'Unreachable'}
              </dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">Database</dt>
              <dd className="flex items-center gap-2 text-sm font-medium text-ink">
                <StatusDot healthy={dbHealthy} />
                {dbHealthy ? 'Healthy' : 'Not reporting'}
              </dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">Environment</dt>
              <dd className="text-sm font-medium capitalize text-ink">
                {version.data?.environment ?? '—'}
              </dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">Version</dt>
              <dd className="text-sm font-medium text-ink tabular">
                {version.data?.version ?? '—'}
              </dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">Commit</dt>
              <dd className="font-mono text-sm text-ink">{version.data?.commit ?? '—'}</dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">Deployed at</dt>
              <dd className="text-sm text-ink">{version.data?.deployedAt || '—'}</dd>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5">
              <dt className="text-sm text-ink-muted">API endpoint</dt>
              <dd className="font-mono text-xs text-ink-muted">{apiBaseUrl}</dd>
            </div>
          </dl>
        )}
        {version.error && (
          <ErrorState message={version.error} onRetry={version.reload} />
        )}
      </section>

      <section className="card">
        <header className="border-b border-border px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Deployment pipeline</h2>
          <p className="mt-0.5 text-xs text-ink-muted">
            Stages Cloud Build runs on every push to main.
          </p>
        </header>
        <ol className="divide-y divide-border">
          {PIPELINE_STAGES.map((stage, index) => (
            <li key={stage.name} className="flex items-start gap-4 px-5 py-3.5">
              <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700 tabular">
                {index + 1}
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-ink">{stage.name}</p>
                <p className="mt-0.5 text-xs text-ink-muted">{stage.detail}</p>
              </div>
            </li>
          ))}
        </ol>
        <p className="border-t border-border px-5 py-3.5 text-xs text-ink-subtle">
          The pipeline fails closed: a failing test or health check stops the deployment, and the
          previous image keeps serving traffic.
        </p>
      </section>
    </div>
  );
}
