/**
 * CareFlow's identity mark: a continuous path stepping through four care
 * touchpoints, echoing the follow-up journey the product manages.
 */
export function CareFlowMark({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 32 32"
      fill="none"
      role="img"
      aria-label="CareFlow"
    >
      <path
        d="M4 21c3.2 0 3.2-10 6.4-10s3.2 10 6.4 10 3.2-10 6.4-10c1.6 0 2.4 2.5 3.2 5"
        stroke="currentColor"
        strokeWidth={2.4}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="26.5" cy="21.5" r="3" fill="currentColor" />
    </svg>
  );
}
