'use client';

import { useState } from 'react';
import { useAuth } from '@/components/providers/AuthProvider';
import { CareJourney } from '@/components/patient/CareJourney';
import { FollowUpResponseForm } from '@/components/patient/FollowUpResponseForm';
import { EmptyState, ErrorState, LoadingSkeleton } from '@/components/ui/States';
import { useApiResource } from '@/hooks/useApiResource';
import { patientApi } from '@/services/careflow-api';
import type { FollowUp } from '@/types/api';

/** Patient-facing view: their own medications, follow-ups and adherence. */
export default function PatientPortalPage() {
  const { user } = useAuth();
  const patientId = user?.patientId ?? null;
  const [activeFollowUp, setActiveFollowUp] = useState<FollowUp | null>(null);

  const patient = useApiResource(
    () => (patientId ? patientApi.getById(patientId) : Promise.resolve(null)),
    [patientId],
  );
  const medications = useApiResource(
    () => (patientId ? patientApi.medications(patientId) : Promise.resolve([])),
    [patientId],
  );
  const followUps = useApiResource(
    () => (patientId ? patientApi.followUps(patientId) : Promise.resolve([])),
    [patientId],
  );
  const adherence = useApiResource(
    () => (patientId ? patientApi.adherence(patientId) : Promise.resolve(null)),
    [patientId],
  );

  if (!patientId) {
    return (
      <div className="mx-auto max-w-3xl">
        <div className="card">
          <EmptyState
            title="No patient record linked"
            description="This account is not yet linked to a patient record. Contact your care team."
          />
        </div>
      </div>
    );
  }

  const nextFollowUp = followUps.data?.find(
    (followUp) => followUp.status === 'SCHEDULED' || followUp.status === 'PENDING',
  );

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-display font-semibold text-ink">
          Hello, {user?.fullName.split(' ')[0]}
        </h1>
        <p className="mt-1 text-sm text-ink-muted">Your care plan and medications.</p>
      </header>

      {nextFollowUp && (
        <section className="card-padded border-brand-200 bg-brand-50">
          <p className="text-sm font-medium text-brand-900">
            {nextFollowUp.title ?? 'Follow-up'} due{' '}
            {new Date(nextFollowUp.scheduledDate).toLocaleDateString(undefined, {
              month: 'long',
              day: 'numeric',
            })}
          </p>
          <p className="mt-1 text-sm text-brand-800">
            Answering a few questions helps your care team stay on top of your recovery.
          </p>
          <button
            type="button"
            onClick={() => setActiveFollowUp(nextFollowUp)}
            className="btn-primary mt-3"
          >
            Answer now
          </button>
        </section>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Adherence</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink">
            {adherence.data ? `${adherence.data.adherencePercentage.toFixed(0)}%` : '—'}
          </p>
        </div>
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Doses taken</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink tabular">
            {adherence.data?.takenDoses ?? '—'}
          </p>
        </div>
        <div className="card-padded">
          <p className="text-sm font-medium text-ink-muted">Doses missed</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-ink tabular">
            {adherence.data?.missedDoses ?? '—'}
          </p>
        </div>
      </div>

      <section className="card">
        <header className="border-b border-border px-5 py-4">
          <h2 className="text-sm font-semibold text-ink">Your medications</h2>
        </header>
        {medications.loading ? (
          <div className="p-5">
            <LoadingSkeleton rows={2} />
          </div>
        ) : medications.error ? (
          <ErrorState message={medications.error} onRetry={medications.reload} />
        ) : medications.data && medications.data.filter((m) => m.active).length > 0 ? (
          <ul className="divide-y divide-border">
            {medications.data
              .filter((medication) => medication.active)
              .map((medication) => (
                <li key={medication.id} className="px-5 py-4">
                  <p className="text-sm font-medium text-ink">{medication.medicineName}</p>
                  <p className="mt-0.5 text-sm text-ink-muted">
                    {medication.dosage} · {medication.frequencyLabel}
                  </p>
                  {medication.instructions && (
                    <p className="mt-1 text-xs text-ink-subtle">{medication.instructions}</p>
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
          <h2 className="text-sm font-semibold text-ink">Your care journey</h2>
        </header>
        <div className="p-5">
          {followUps.loading ? (
            <LoadingSkeleton rows={4} />
          ) : followUps.data && followUps.data.length > 0 ? (
            <CareJourney
              dischargeDate={patient.data?.dischargeDate}
              followUps={followUps.data}
              onSelect={setActiveFollowUp}
            />
          ) : (
            <EmptyState title="No follow-ups scheduled" />
          )}
        </div>
      </section>

      {activeFollowUp && (
        <FollowUpResponseForm
          followUp={activeFollowUp}
          onClose={() => setActiveFollowUp(null)}
          onSubmitted={() => {
            setActiveFollowUp(null);
            followUps.reload();
            adherence.reload();
          }}
        />
      )}
    </div>
  );
}
