import Link from 'next/link';
import type { ReactNode } from 'react';

interface Props {
  label: string;
  value: ReactNode;
  hint?: string;
  href?: string;
  tone?: 'default' | 'warning' | 'critical';
}

const toneStyles = {
  default: 'text-ink',
  warning: 'text-amber-700',
  critical: 'text-red-700',
} as const;

export function StatCard({ label, value, hint, href, tone = 'default' }: Props) {
  const content = (
    <>
      <p className="text-sm font-medium text-ink-muted">{label}</p>
      <p className={`mt-2 text-3xl font-semibold tracking-tight ${toneStyles[tone]}`}>{value}</p>
      {hint && <p className="mt-1.5 text-xs text-ink-subtle">{hint}</p>}
    </>
  );

  if (href) {
    return (
      <Link
        href={href}
        className="card-padded block transition-shadow hover:shadow-raised focus-visible:shadow-raised"
      >
        {content}
      </Link>
    );
  }

  return <div className="card-padded">{content}</div>;
}
