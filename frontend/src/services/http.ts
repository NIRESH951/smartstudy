import type { ApiErrorBody, CsrfTokenResponse, FieldErrorDetail } from '../types/api';

/**
 * Thin fetch wrapper for the SmartStudy REST API.
 *
 * - Sends cookies (`credentials: 'include'`) so the HttpOnly session cookie is used.
 * - Fetches the CSRF token once from GET /auth/csrf and sends it in the X-CSRF-TOKEN header on
 *   every state-changing request. If the server reports CSRF_INVALID (e.g. after the session
 *   expired) the token is refreshed and the request retried once.
 * - Converts error responses into {@link ApiError} with the server's message and field errors.
 */

export const API_BASE_URL: string = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrorDetail[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldErrorDetail[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  /** Field errors as a map, convenient for showing messages next to form inputs. */
  fieldErrorMap(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const error of this.fieldErrors) {
      const key = error.field.split('.').pop() ?? error.field;
      if (!map[key]) map[key] = error.message;
    }
    return map;
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

let csrfToken: { header: string; value: string } | null = null;
let unauthorizedHandler: (() => void) | null = null;

/** Registers a callback invoked when the session has expired (HTTP 401). */
export function onUnauthorized(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** Forgets the CSRF token; call after login and logout because the server rotates it. */
export function resetCsrfToken(): void {
  csrfToken = null;
}

async function loadCsrfToken(force = false): Promise<{ header: string; value: string }> {
  if (csrfToken && !force) return csrfToken;
  const response = await fetch(`${API_BASE_URL}/auth/csrf`, {
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new ApiError(response.status, 'CSRF_UNAVAILABLE', 'Could not reach the SmartStudy server.');
  }
  const body = (await response.json()) as CsrfTokenResponse;
  csrfToken = { header: body.headerName, value: body.token };
  return csrfToken;
}

async function readError(response: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> | null = null;
  try {
    body = (await response.json()) as Partial<ApiErrorBody>;
  } catch {
    body = null;
  }
  const fallback =
    response.status >= 500
      ? 'The server had a problem. Please try again.'
      : response.status === 404
        ? 'Not found.'
        : `Request failed (${response.status}).`;
  return new ApiError(
    response.status,
    body?.code ?? `HTTP_${response.status}`,
    body?.message ?? fallback,
    body?.fieldErrors ?? [],
  );
}

export async function request<T>(method: Method, path: string, body?: unknown, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const token = await loadCsrfToken();
    headers[token.header] = token.value;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      credentials: 'include',
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot connect to the SmartStudy server. Is the backend running?');
  }

  if (response.ok) {
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    return (text ? JSON.parse(text) : undefined) as T;
  }

  const error = await readError(response);
  if (response.status === 403 && error.code === 'CSRF_INVALID' && !retried) {
    await loadCsrfToken(true);
    return request<T>(method, path, body, true);
  }
  if (response.status === 401 && !path.startsWith('/auth/')) {
    unauthorizedHandler?.();
  }
  throw error;
}

export const http = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, body),
  delete: <T = void>(path: string) => request<T>('DELETE', path),
};

/** Human-readable message for any thrown value. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Something went wrong.';
}
