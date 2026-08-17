'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import { useAuth } from '@/components/providers/AuthProvider';
import { useToast } from '@/components/providers/ToastProvider';
import { CareFlowMark } from '@/components/ui/CareFlowMark';
import { ApiRequestError } from '@/services/api-client';

export default function LoginPage() {
  const { user, initialising, login } = useAuth();
  const router = useRouter();
  const toast = useToast();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!initialising && user) {
      router.replace(user.role === 'PATIENT' ? '/portal' : '/dashboard');
    }
  }, [initialising, user, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setFormError(null);

    try {
      const profile = await login(email.trim(), password);
      toast.success(`Welcome back, ${profile.fullName.split(' ')[0]}.`);
      router.replace(profile.role === 'PATIENT' ? '/portal' : '/dashboard');
    } catch (cause) {
      const message =
        cause instanceof ApiRequestError
          ? cause.message
          : 'Unable to sign in. Please try again.';
      setFormError(message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen">
      <div className="flex w-full flex-col justify-center px-6 py-12 lg:w-[52%] lg:px-16 xl:px-24">
        <div className="mx-auto w-full max-w-sm">
          <div className="mb-10 flex items-center gap-2.5">
            <CareFlowMark className="h-8 w-8 text-brand-600" />
            <div>
              <p className="text-lg font-semibold tracking-tight text-ink">CareFlow</p>
              <p className="text-xs text-ink-subtle">Continuity care, connected.</p>
            </div>
          </div>

          <h1 className="text-2xl font-semibold tracking-tight text-ink">Sign in</h1>
          <p className="mt-2 text-sm text-ink-muted">
            Access your continuity care workspace.
          </p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-5" noValidate>
            <div>
              <label htmlFor="email" className="label mb-1.5">
                Email address
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="input"
                placeholder="you@careflow.health"
                aria-describedby={formError ? 'login-error' : undefined}
              />
            </div>

            <div>
              <label htmlFor="password" className="label mb-1.5">
                Password
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="input"
                placeholder="••••••••••"
                aria-describedby={formError ? 'login-error' : undefined}
              />
            </div>

            {formError && (
              <div
                id="login-error"
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-800"
              >
                {formError}
              </div>
            )}

            <button type="submit" disabled={submitting} className="btn-primary w-full">
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>

          <p className="mt-8 text-xs leading-relaxed text-ink-subtle">
            CareFlow surfaces operational signals and routes cases to qualified care
            managers. It does not diagnose conditions or recommend treatment.
          </p>
        </div>
      </div>

      <div className="relative hidden bg-brand-700 lg:block lg:w-[48%]">
        <div className="flex h-full flex-col justify-center px-16 xl:px-20">
          <blockquote className="max-w-md">
            <p className="text-2xl font-medium leading-snug tracking-tight text-white">
              Every discharged patient gets a follow-up plan, and every missed signal
              reaches a human who can act on it.
            </p>
            <footer className="mt-8 border-t border-white/20 pt-6">
              <p className="text-sm text-brand-100">
                Post-discharge follow-ups · Medication adherence · Human escalation
              </p>
            </footer>
          </blockquote>

          <dl className="mt-14 grid max-w-md grid-cols-3 gap-6">
            {[
              { value: '5', label: 'Touchpoints per high-risk plan' },
              { value: '100%', label: 'Actions written to the audit trail' },
              { value: '0', label: 'Automated care decisions' },
            ].map((stat) => (
              <div key={stat.label}>
                <dt className="text-2xl font-semibold text-white tabular">{stat.value}</dt>
                <dd className="mt-1 text-xs leading-relaxed text-brand-200">{stat.label}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
    </div>
  );
}
