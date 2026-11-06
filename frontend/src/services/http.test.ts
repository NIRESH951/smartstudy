import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, http, onUnauthorized, resetCsrfToken } from './http';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const csrf = (token: string) => json(200, { headerName: 'X-CSRF-TOKEN', parameterName: '_csrf', token });

describe('http client', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    resetCsrfToken();
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    onUnauthorized(null);
    vi.unstubAllGlobals();
  });

  it('sends GET requests with cookies and without a CSRF token', async () => {
    fetchMock.mockResolvedValueOnce(json(200, [{ id: 1 }]));

    const result = await http.get<{ id: number }[]>('/subjects');

    expect(result).toEqual([{ id: 1 }]);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/subjects');
    expect(init?.credentials).toBe('include');
    expect((init?.headers as Record<string, string>)['X-CSRF-TOKEN']).toBeUndefined();
  });

  it('fetches the CSRF token once and sends it on state-changing requests', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf('token-1'))
      .mockResolvedValueOnce(json(201, { id: 7 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));

    await http.post('/subjects', { name: 'Physics' });
    await http.delete('/subjects/7');

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/auth/csrf');
    const postHeaders = fetchMock.mock.calls[1][1]?.headers as Record<string, string>;
    expect(postHeaders['X-CSRF-TOKEN']).toBe('token-1');
    expect(postHeaders['Content-Type']).toBe('application/json');
    const deleteHeaders = fetchMock.mock.calls[2][1]?.headers as Record<string, string>;
    expect(deleteHeaders['X-CSRF-TOKEN']).toBe('token-1');
  });

  it('refreshes the CSRF token and retries once when the server rejects it', async () => {
    fetchMock
      .mockResolvedValueOnce(csrf('stale'))
      .mockResolvedValueOnce(json(403, { code: 'CSRF_INVALID', message: 'Invalid CSRF token' }))
      .mockResolvedValueOnce(csrf('fresh'))
      .mockResolvedValueOnce(json(200, { ok: true }));

    const result = await http.put<{ ok: boolean }>('/users/me', { fullName: 'A' });

    expect(result).toEqual({ ok: true });
    const retryHeaders = fetchMock.mock.calls[3][1]?.headers as Record<string, string>;
    expect(retryHeaders['X-CSRF-TOKEN']).toBe('fresh');
  });

  it('turns error responses into ApiError with field errors', async () => {
    fetchMock.mockResolvedValueOnce(csrf('t')).mockResolvedValueOnce(
      json(400, {
        code: 'VALIDATION_FAILED',
        message: 'Validation failed',
        fieldErrors: [{ field: 'windows[0].startTime', message: 'must not be null' }],
      }),
    );

    const error = await http.put('/availability', {}).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.fieldErrorMap()).toEqual({ startTime: 'must not be null' });
  });

  it('calls the unauthorized handler on 401 outside the auth endpoints', async () => {
    const handler = vi.fn();
    onUnauthorized(handler);
    fetchMock.mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED', message: 'Sign in' }));
    fetchMock.mockResolvedValueOnce(json(401, { code: 'UNAUTHENTICATED', message: 'Sign in' }));

    await expect(http.get('/subjects')).rejects.toBeInstanceOf(ApiError);
    await expect(http.get('/auth/me')).rejects.toBeInstanceOf(ApiError);

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('reports a friendly message when the backend is unreachable', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    await expect(http.get('/subjects')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });
});
