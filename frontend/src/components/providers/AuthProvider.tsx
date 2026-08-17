'use client';

import { useRouter } from 'next/navigation';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { setUnauthorizedHandler, tokenStorage } from '@/services/api-client';
import { authApi } from '@/services/careflow-api';
import type { UserProfile } from '@/types/api';

interface AuthContextValue {
  user: UserProfile | null;
  /** True until the stored token has been validated against the API. */
  initialising: boolean;
  login: (email: string, password: string) => Promise<UserProfile>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<UserProfile | null>(null);
  const [initialising, setInitialising] = useState(true);

  const logout = useCallback(() => {
    tokenStorage.clear();
    setUser(null);
    router.replace('/login');
  }, [router]);

  // A 401 from any request means the token is gone or expired; drop the session
  // rather than leaving the UI in a half-authenticated state.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      router.replace('/login');
    });
    return () => setUnauthorizedHandler(null);
  }, [router]);

  // Restore the session on load by validating the stored token with the server,
  // so a revoked or expired token cannot leave a stale user in the UI.
  useEffect(() => {
    let cancelled = false;

    async function restoreSession() {
      if (!tokenStorage.get()) {
        setInitialising(false);
        return;
      }
      try {
        const profile = await authApi.me();
        if (!cancelled) setUser(profile);
      } catch {
        tokenStorage.clear();
      } finally {
        if (!cancelled) setInitialising(false);
      }
    }

    void restoreSession();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    tokenStorage.set(response.accessToken);
    setUser(response.user);
    return response.user;
  }, []);

  const value = useMemo(
    () => ({ user, initialising, login, logout }),
    [user, initialising, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider.');
  }
  return context;
}
