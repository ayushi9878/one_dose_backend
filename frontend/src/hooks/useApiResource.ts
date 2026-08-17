'use client';

import { useCallback, useEffect, useState } from 'react';
import { ApiRequestError } from '@/services/api-client';

interface Settled<T> {
  /** The request key this result came from, used to ignore stale responses. */
  key: string;
  data: T | null;
  message: string | null;
}

interface ResourceState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  reload: () => void;
}

/**
 * Loads a resource and tracks its loading and error states.
 *
 * Each distinct set of inputs forms a request *key*. Loading is then simply
 * "the settled result is not for the current key" — derived during render from
 * ordinary state, with no synchronous setState in the effect and no ref read
 * during render. The key also discards out-of-order responses: a slow earlier
 * request cannot overwrite a newer one's result.
 */
export function useApiResource<T>(
  fetcher: () => Promise<T>,
  deps: readonly unknown[] = [],
): ResourceState<T> {
  const [reloadToken, setReloadToken] = useState(0);
  const requestKey = JSON.stringify([deps, reloadToken]);

  const [settled, setSettled] = useState<Settled<T>>({
    key: '',
    data: null,
    message: null,
  });

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  useEffect(() => {
    let active = true;

    fetcher()
      .then((result) => {
        if (!active) return;
        setSettled({ key: requestKey, data: result, message: null });
      })
      .catch((cause: unknown) => {
        if (!active) return;
        // A 401 is handled globally by redirecting to login; surfacing it here
        // too would flash a spurious error before the redirect lands.
        if (cause instanceof ApiRequestError && cause.isUnauthorized) {
          setSettled((previous) => ({ ...previous, key: requestKey }));
          return;
        }
        setSettled((previous) => ({
          key: requestKey,
          data: previous.data,
          message:
            cause instanceof Error ? cause.message : 'Unable to load this information.',
        }));
      });

    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestKey]);

  const isCurrent = settled.key === requestKey;

  return {
    // Previously loaded data stays visible while a refresh is in flight, so a
    // reload never blanks the screen.
    data: settled.data,
    loading: !isCurrent,
    error: isCurrent ? settled.message : null,
    reload,
  };
}
