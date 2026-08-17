import type {
  EscalationSeverity,
  EscalationStatus,
  FollowUpStatus,
  RiskLevel,
} from '@/types/api';

const riskStyles: Record<RiskLevel, string> = {
  NONE: 'bg-slate-100 text-slate-700',
  LOW: 'bg-emerald-50 text-emerald-800',
  MEDIUM: 'bg-amber-50 text-amber-900',
  HIGH: 'bg-red-50 text-red-800',
};

const riskLabels: Record<RiskLevel, string> = {
  NONE: 'No signal',
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
};

export function RiskBadge({ level }: { level: RiskLevel }) {
  return (
    <span className={`badge ${riskStyles[level]}`}>
      <span
        className="h-1.5 w-1.5 rounded-full bg-current opacity-70"
        aria-hidden="true"
      />
      {riskLabels[level]} risk
    </span>
  );
}

const followUpStyles: Record<FollowUpStatus, string> = {
  SCHEDULED: 'bg-slate-100 text-slate-700',
  PENDING: 'bg-blue-50 text-blue-800',
  COMPLETED: 'bg-emerald-50 text-emerald-800',
  MISSED: 'bg-amber-50 text-amber-900',
  ESCALATED: 'bg-red-50 text-red-800',
};

const followUpLabels: Record<FollowUpStatus, string> = {
  SCHEDULED: 'Scheduled',
  PENDING: 'Pending',
  COMPLETED: 'Completed',
  MISSED: 'Missed',
  ESCALATED: 'Escalated',
};

export function FollowUpBadge({ status }: { status: FollowUpStatus }) {
  return <span className={`badge ${followUpStyles[status]}`}>{followUpLabels[status]}</span>;
}

const severityStyles: Record<EscalationSeverity, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-50 text-amber-900',
  HIGH: 'bg-red-50 text-red-800',
  CRITICAL: 'bg-red-100 text-red-900',
};

export function SeverityBadge({ severity }: { severity: EscalationSeverity }) {
  return (
    <span className={`badge ${severityStyles[severity]}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current opacity-70" aria-hidden="true" />
      {severity.charAt(0) + severity.slice(1).toLowerCase()}
    </span>
  );
}

const escalationStatusStyles: Record<EscalationStatus, string> = {
  PENDING: 'bg-amber-50 text-amber-900',
  ASSIGNED: 'bg-blue-50 text-blue-800',
  IN_REVIEW: 'bg-indigo-50 text-indigo-800',
  RESOLVED: 'bg-emerald-50 text-emerald-800',
};

const escalationStatusLabels: Record<EscalationStatus, string> = {
  PENDING: 'Pending',
  ASSIGNED: 'Assigned',
  IN_REVIEW: 'In review',
  RESOLVED: 'Resolved',
};

export function EscalationStatusBadge({ status }: { status: EscalationStatus }) {
  return (
    <span className={`badge ${escalationStatusStyles[status]}`}>
      {escalationStatusLabels[status]}
    </span>
  );
}
