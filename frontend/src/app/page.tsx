'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useAuth } from '@/components/providers/AuthProvider';
import { CareFlowMark } from '@/components/ui/CareFlowMark';

export default function HomePage() {
  const { user, initialising } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (initialising) return;
    if (!user) {
      router.replace('/login');
    } else {
      router.replace(user.role === 'PATIENT' ? '/portal' : '/dashboard');
    }
  }, [initialising, user, router]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-canvas">
      <CareFlowMark className="h-8 w-8 animate-pulse text-brand-600" />
    </div>
  );
}
