export type RegistrationResponse = {
  userId: string;
  email: string;
  emailVerificationRequired: boolean;
};

export type TokenPair = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  accessTokenExpiresAt: string;
  refreshTokenExpiresAt: string;
  sessionId: string;
};

export type AuthSession = {
  sessionId: string;
  createdAt: string;
  lastUsedAt: string;
  expiresAt: string;
  userAgent: string | null;
  ipAddress: string | null;
  current: boolean;
};

export type ValidationIssue = {
  field?: string;
  pointer?: string;
  message?: string;
  code?: string;
  [key: string]: unknown;
};

export type ProblemDetails = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: ValidationIssue[];
  [key: string]: unknown;
};

export type JwtSnapshot = {
  header: Record<string, unknown> | null;
  claims: Record<string, unknown> | null;
};

export type ActivityEntry = {
  id: string;
  at: string;
  method: string;
  path: string;
  status: number;
  durationMs: number;
  retryAfter?: string | null;
  problemCode?: string | null;
  outcome: 'success' | 'error';
};
