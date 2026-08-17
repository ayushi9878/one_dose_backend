'use client';

import type { FollowUp, FollowUpStatus } from '@/types/api';

const stateStyles: Record<FollowUpStatus, { dot: string; ring: string; label: string }> = {
  COMPLETED: {
    dot: 'bg-emerald-600',
    ring: 'ring-emerald-100',
    label: 'Completed',
  },
  SCHEDULED: {
    dot: 'bg-slate-300',
    ring: 'ring-slate-100',
    label: 'Upcoming',
  },
  PENDING: {
    dot: 'bg-blue-500',
    ring: 'ring-blue-100',
    label: 'Pending',
  },
  MISSED: {
    dot: 'bg-amber-500',
    ring: 'ring-amber-100',
    label: 'Missed',
  },
  ESCALATED: {
    dot: 'bg-red-600',
    ring: 'ring-red-100',
    label: 'Escalated',
  },
};

interface Props {
  dischargeDate?: string | null;
  followUps: FollowUp[];
  onSelect?: (followUp: FollowUp) => void;
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/**
 * The post-discharge care journey. Each touchpoint carries its state as a
 * labelled chip as well as a colour, so the timeline is readable without
 * relying on hue alone.
 */
export function CareJourney({ dischargeDate, followUps, onSelect }: Props) {
  const ordered = [...followUps].sort(
    (a, b) => new Date(a.scheduledDate).getTime() - new Date(b.scheduledDate).getTime(),
  );

  return (
    <ol className="relative space-y-0" aria-label="Care journey timeline">
      {dischargeDate && (
        <li className="relative flex gap-4 pb-6">
          <div className="absolute left-[9px] top-6 h-full w-px bg-border" aria-hidden="true" />
          <div className="relative z-10 mt-1 h-[18px] w-[18px] shrink-0 rounded-full bg-brand-600 ring-4 ring-brand-50" />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-ink">Discharged</p>
            <p className="mt-0.5 text-xs text-ink-muted tabular">{formatDate(dischargeDate)}</p>
          </div>
        </li>
      )}

      {ordered.map((followUp, index) => {
        const style = stateStyles[followUp.status];
        const isLast = index === ordered.length - 1;
        const interactive = Boolean(onSelect) && followUp.status !== 'COMPLETED';

        const content = (
          <>
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm font-medium text-ink">
                {followUp.title ?? `Day ${followUp.dayOffset ?? '—'} check-in`}
              </p>
              <span className="text-xs text-ink-subtle">{style.label}</span>
              {followUp.overdue && followUp.status !== 'MISSED' && (
                <span className="badge bg-amber-50 text-amber-900">Overdue</span>
              )}
            </div>
            <p className="mt-0.5 text-xs text-ink-muted tabular">
              {formatDate(followUp.scheduledDate)}
              {followUp.completedDate &&
                ` · answered ${formatDate(followUp.completedDate)}`}
            </p>
          </>
        );

        return (
          <li key={followUp.id} className={`relative flex gap-4 ${isLast ? '' : 'pb-6'}`}>
            {!isLast && (
              <div className="absolute left-[9px] top-6 h-full w-px bg-border" aria-hidden="true" />
            )}
            <div
              className={`relative z-10 mt-1 h-[18px] w-[18px] shrink-0 rounded-full ring-4 ${style.dot} ${style.ring}`}
            />
            <div className="min-w-0 flex-1">
              {interactive ? (
                <button
                  type="button"
                  onClick={() => onSelect?.(followUp)}
                  className="w-full rounded-lg px-2 py-1 text-left transition-colors hover:bg-surface-muted"
                >
                  {content}
                </button>
              ) : (
                <div className="px-2 py-1">{content}</div>
              )}
            </div>
          </li>
        );
      })}
    </ol>
  );
}
