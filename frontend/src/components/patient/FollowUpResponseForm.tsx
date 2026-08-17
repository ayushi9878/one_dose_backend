'use client';

import { useState, type FormEvent } from 'react';
import { useToast } from '@/components/providers/ToastProvider';
import { ApiRequestError } from '@/services/api-client';
import { followUpApi } from '@/services/careflow-api';
import type { FollowUp, PatientResponseResult } from '@/types/api';

interface Props {
  followUp: FollowUp;
  onClose: () => void;
  onSubmitted: (result: PatientResponseResult) => void;
}

export function FollowUpResponseForm({ followUp, onClose, onSubmitted }: Props) {
  const toast = useToast();

  const [medicationTaken, setMedicationTaken] = useState(true);
  const [missedDoses, setMissedDoses] = useState(0);
  const [symptomsReported, setSymptomsReported] = useState(false);
  const [symptomsText, setSymptomsText] = useState('');
  const [refillNeeded, setRefillNeeded] = useState(false);
  const [notes, setNotes] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    const symptoms = symptomsReported
      ? symptomsText
          .split(',')
          .map((symptom) => symptom.trim())
          .filter(Boolean)
      : [];

    try {
      const result = await followUpApi.submitResponse(followUp.id, {
        medicationTaken,
        missedDoses,
        symptomsReported,
        symptoms,
        refillNeeded,
        notes: notes.trim() || undefined,
      });

      if (result.escalation) {
        toast.notify({
          tone: 'error',
          title: `${result.escalation.severity} risk — escalation opened`,
          description: 'The case has been routed to a care manager for review.',
        });
      } else {
        toast.success('Response recorded', 'Adherence has been updated.');
      }
      onSubmitted(result);
    } catch (cause) {
      setError(
        cause instanceof ApiRequestError
          ? cause.message
          : 'Unable to record this response. Please try again.',
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/30 p-4 sm:items-center">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="response-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-card bg-surface shadow-overlay"
      >
        <header className="flex items-start justify-between gap-4 border-b border-border px-5 py-4">
          <div>
            <h2 id="response-title" className="text-base font-semibold text-ink">
              {followUp.title ?? 'Follow-up response'}
            </h2>
            <p className="mt-0.5 text-sm text-ink-muted">
              {followUp.patientName} ·{' '}
              {new Date(followUp.scheduledDate).toLocaleDateString(undefined, {
                month: 'long',
                day: 'numeric',
              })}
            </p>
          </div>
          <button type="button" onClick={onClose} className="btn-ghost -mr-2 p-2" aria-label="Close">
            <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </header>

        <form onSubmit={handleSubmit} className="space-y-5 px-5 py-5">
          <fieldset>
            <legend className="label mb-2">Has the patient been taking their medication?</legend>
            <div className="flex gap-2">
              {[
                { value: true, label: 'Yes' },
                { value: false, label: 'No' },
              ].map((option) => (
                <button
                  key={option.label}
                  type="button"
                  onClick={() => setMedicationTaken(option.value)}
                  aria-pressed={medicationTaken === option.value}
                  className={`flex-1 rounded-lg border px-4 py-2 text-sm font-medium transition-colors
                              ${
                                medicationTaken === option.value
                                  ? 'border-brand-600 bg-brand-50 text-brand-700'
                                  : 'border-border-strong text-ink-muted hover:bg-surface-muted'
                              }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </fieldset>

          <div>
            <label htmlFor="missed-doses" className="label mb-1.5">
              Missed doses since the last check-in
            </label>
            <input
              id="missed-doses"
              type="number"
              min={0}
              max={500}
              value={missedDoses}
              onChange={(event) => setMissedDoses(Math.max(0, Number(event.target.value)))}
              className="input"
            />
          </div>

          <fieldset>
            <legend className="label mb-2">Has the patient reported any symptoms?</legend>
            <div className="flex gap-2">
              {[
                { value: false, label: 'No' },
                { value: true, label: 'Yes' },
              ].map((option) => (
                <button
                  key={option.label}
                  type="button"
                  onClick={() => setSymptomsReported(option.value)}
                  aria-pressed={symptomsReported === option.value}
                  className={`flex-1 rounded-lg border px-4 py-2 text-sm font-medium transition-colors
                              ${
                                symptomsReported === option.value
                                  ? 'border-brand-600 bg-brand-50 text-brand-700'
                                  : 'border-border-strong text-ink-muted hover:bg-surface-muted'
                              }`}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </fieldset>

          {symptomsReported && (
            <div>
              <label htmlFor="symptoms" className="label mb-1.5">
                Symptoms as described by the patient
              </label>
              <input
                id="symptoms"
                type="text"
                value={symptomsText}
                onChange={(event) => setSymptomsText(event.target.value)}
                placeholder="dizziness, fatigue"
                className="input"
                aria-describedby="symptoms-hint"
              />
              <p id="symptoms-hint" className="mt-1 text-xs text-ink-subtle">
                Separate with commas. Recorded verbatim; CareFlow does not interpret them clinically.
              </p>
            </div>
          )}

          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              checked={refillNeeded}
              onChange={(event) => setRefillNeeded(event.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-border-strong text-brand-600 focus:ring-brand-500"
            />
            <span className="text-sm text-ink">A medication refill is needed</span>
          </label>

          <div>
            <label htmlFor="notes" className="label mb-1.5">
              Notes <span className="font-normal text-ink-subtle">(optional)</span>
            </label>
            <textarea
              id="notes"
              rows={3}
              maxLength={1000}
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              className="input resize-none"
              placeholder="Anything the care manager should know."
            />
          </div>

          {error && (
            <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-800">
              {error}
            </div>
          )}

          <div className="flex justify-end gap-2 border-t border-border pt-4">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancel
            </button>
            <button type="submit" disabled={submitting} className="btn-primary">
              {submitting ? 'Recording…' : 'Record response'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
