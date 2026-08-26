import { activityStore } from './activity';
import { getAccessToken, refreshAuthentication } from './session';
import type { AuthSession, ProblemDetails, RegistrationResponse, TokenPair } from './types';

const configuredBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.trim();
export const API_BASE_URL = (configuredBaseUrl || 'http://localhost:8080').replace(/\/$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetails | null;
  readonly retryAfter: string | null;

  constructor(status: number, problem: ProblemDetails | null, retryAfter: string | null) {
    super(problem?.detail || problem?.title || `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.retryAfter = retryAfter;
  }
}

type RequestOptions = Omit<RequestInit, 'body'> & {
  body?: unknown;
  auth?: boolean;
  retryAuth?: boolean;
};

async function parseResponseBody(response: Response) {
  if (response.status === 204 || response.headers.get('content-length') === '0') return null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('json')) return response.json().catch(() => null);
  return response.text().catch(() => null);
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const startedAt = performance.now();
  const method = (options.method || 'GET').toUpperCase();
  const auth = options.auth ?? false;
  const headers = new Headers(options.headers);

  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  if (auth) {
    const accessToken = getAccessToken();
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
  }

  const execute = () => fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  let response = await execute();
  if (auth && response.status === 401 && options.retryAuth !== false) {
    const refreshed = await refreshAuthentication();
    if (refreshed) {
      const accessToken = getAccessToken();
      if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`);
      response = await execute();
    }
  }

  const payload = await parseResponseBody(response);
  const retryAfter = response.headers.get('retry-after');
  const problem = !response.ok && payload && typeof payload === 'object' ? payload as ProblemDetails : null;

  activityStore.add({
    method,
    path,
    status: response.status,
    durationMs: Math.max(1, Math.round(performance.now() - startedAt)),
    retryAfter,
    problemCode: problem?.code ? String(problem.code) : null,
    outcome: response.ok ? 'success' : 'error',
  });

  if (!response.ok) throw new ApiError(response.status, problem, retryAfter);
  return payload as T;
}

export const authApi = {
  register: (email: string, password: string) => apiRequest<RegistrationResponse>('/api/v1/auth/register', { method: 'POST', body: { email, password } }),
  login: (email: string, password: string) => apiRequest<TokenPair>('/api/v1/auth/login', { method: 'POST', body: { email, password } }),
  refresh: (refreshToken: string) => apiRequest<TokenPair>('/api/v1/auth/refresh', { method: 'POST', body: { refreshToken }, retryAuth: false }),
  resendVerification: (email: string) => apiRequest<null>('/api/v1/auth/email-verification', { method: 'POST', body: { email } }),
  confirmVerification: (token: string) => apiRequest<null>('/api/v1/auth/email-verification/confirm', { method: 'POST', body: { token } }),
  requestPasswordReset: (email: string) => apiRequest<null>('/api/v1/auth/password-reset', { method: 'POST', body: { email } }),
  confirmPasswordReset: (token: string, newPassword: string) => apiRequest<null>('/api/v1/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword } }),
  sessions: () => apiRequest<AuthSession[]>('/api/v1/auth/sessions', { auth: true }),
  revokeSession: (sessionId: string) => apiRequest<null>(`/api/v1/auth/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE', auth: true }),
  logout: () => apiRequest<null>('/api/v1/auth/logout', { method: 'POST', auth: true }),
  logoutAll: () => apiRequest<null>('/api/v1/auth/logout-all', { method: 'POST', auth: true }),
};
