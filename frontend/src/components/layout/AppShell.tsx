'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useState, type ReactNode } from 'react';
import { useAuth } from '@/components/providers/AuthProvider';
import { CareFlowMark } from '@/components/ui/CareFlowMark';

interface NavItem {
  href: string;
  label: string;
  icon: string;
  roles?: Array<'ADMIN' | 'CARE_MANAGER' | 'PATIENT'>;
}

const NAV_ITEMS: NavItem[] = [
  {
    href: '/dashboard',
    label: 'Dashboard',
    icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6',
    roles: ['ADMIN', 'CARE_MANAGER'],
  },
  {
    href: '/patients',
    label: 'Patients',
    icon: 'M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z',
    roles: ['ADMIN', 'CARE_MANAGER'],
  },
  {
    href: '/escalations',
    label: 'Escalations',
    icon: 'M12 9v2m0 4h.01M5.07 19H19a2 2 0 001.75-2.94l-6.93-12a2 2 0 00-3.5 0l-6.93 12A2 2 0 005.07 19z',
    roles: ['ADMIN', 'CARE_MANAGER'],
  },
  {
    href: '/system',
    label: 'System',
    icon: 'M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01',
  },
];

export function AppShell({ children }: { children: ReactNode }) {
  const { user, initialising, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  // The mobile drawer is derived from the route it was opened on, so navigating
  // closes it without an effect syncing state back into React.
  const [openedAtPath, setOpenedAtPath] = useState<string | null>(null);
  const sidebarOpen = openedAtPath === pathname;

  useEffect(() => {
    if (!initialising && !user) {
      router.replace('/login');
    }
  }, [initialising, user, router]);

  if (initialising) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-canvas">
        <div className="flex flex-col items-center gap-3">
          <CareFlowMark className="h-8 w-8 animate-pulse text-brand-600" />
          <p className="text-sm text-ink-muted">Loading CareFlow…</p>
        </div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || item.roles.includes(user.role),
  );

  const roleLabel = user.role
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');

  return (
    <div className="min-h-screen bg-canvas">
      {sidebarOpen && (
        <button
          type="button"
          aria-label="Close navigation"
          onClick={() => setOpenedAtPath(null)}
          className="fixed inset-0 z-30 bg-ink/20 lg:hidden"
        />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-border
                    bg-surface transition-transform lg:translate-x-0
                    ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        <div className="flex h-16 items-center gap-2.5 border-b border-border px-5">
          <CareFlowMark className="h-7 w-7 text-brand-600" />
          <div>
            <p className="text-sm font-semibold tracking-tight text-ink">CareFlow</p>
            <p className="text-[11px] text-ink-subtle">Continuity care, connected.</p>
          </div>
        </div>

        <nav className="flex-1 space-y-1 p-3" aria-label="Main navigation">
          {visibleItems.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? 'page' : undefined}
                className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors
                            ${
                              active
                                ? 'bg-brand-50 text-brand-700'
                                : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
                            }`}
              >
                <svg
                  className="h-[18px] w-[18px] shrink-0"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth={1.8}
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d={item.icon} />
                </svg>
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="border-t border-border p-3">
          <div className="flex items-center gap-3 rounded-lg px-2 py-2">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700">
              {user.fullName
                .split(' ')
                .map((part) => part.charAt(0))
                .slice(0, 2)
                .join('')}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-ink">{user.fullName}</p>
              <p className="truncate text-xs text-ink-subtle">{roleLabel}</p>
            </div>
          </div>
          <button type="button" onClick={logout} className="btn-ghost mt-1 w-full justify-start">
            <svg className="h-[18px] w-[18px]" fill="none" stroke="currentColor" strokeWidth={1.8} viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
              />
            </svg>
            Sign out
          </button>
        </div>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-surface/85 px-4 backdrop-blur lg:px-8">
          <button
            type="button"
            onClick={() => setOpenedAtPath(pathname)}
            className="btn-ghost -ml-2 p-2 lg:hidden"
            aria-label="Open navigation"
          >
            <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={1.8} viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </header>

        <main className="px-4 py-6 lg:px-8 lg:py-8">{children}</main>
      </div>
    </div>
  );
}
