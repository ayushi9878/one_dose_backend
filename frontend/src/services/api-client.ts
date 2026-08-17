import type { ApiError } from '@/types/api';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';
const TOKEN_STORAGE_KEY = 'careflow.token';

/**
 * Error carrying the backend's structured problem details, so callers can show
 * the server's message and field errors rather than a generic failure string.
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;
  readonly requestId?: string;

  constructor(status: number, message: string, options?: {
    fieldErrors?: Record<string, string>;
    requestId?: string;
  }) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.fieldErrors = options?.fieldErrors;
    this.requestId = options?.requestId;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }
}

export const tokenStorage = {
  get(): string | null {
    if (typeof window === 'undefined') return null;
    return window.localStorage.getItem(TOKEN_STORAGE_KEY);
  },
  set(token: string): void {
    if (typeof window === 'undefined') return;
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
  },
  clear(): void {
    if (typeof window === 'undefined') return;
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
  },
};

/** Notified on 401 so the app can return the user to the login screen. */
type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  onUnauthorized = handler;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  const url = new URL(path, API_BASE_URL);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

async function toApiError(response: Response): Promise<ApiRequestError> {
  let payload: Partial<ApiError> = {};
  try {
    payload = (await response.json()) as Partial<ApiError>;
  } catch {
    // A non-JSON body (proxy error page, network failure) leaves payload empty.
  }

  const message =
    payload.message ??
    (response.status === 401
      ? 'Your session has expired. Please sign in again.'
      : `Request failed with status ${response.status}.`);

  return new ApiRequestError(response.status, message, {
    fieldErrors: payload.fieldErrors,
    requestId: payload.requestId,
  });
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, signal } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const token = tokenStorage.get();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiRequestError(0, 'Unable to reach the CareFlow API. Check your connection.');
  }

  if (response.status === 401) {
    tokenStorage.clear();
    onUnauthorized?.();
    throw await toApiError(response);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export const apiBaseUrl = API_BASE_URL;
