import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import { authApi } from './api';
import { configureAuthBridge } from './session';
import type { JwtSnapshot, TokenPair } from './types';

const STORAGE_KEY = 'authlab.session.v1';

type StoredAuth = { tokens: TokenPair; email?: string | null };
type AuthContextValue = {
  tokens: TokenPair | null;
  email: string | null;
  jwt: JwtSnapshot;
  authenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  refresh: () => Promise<boolean>;
  logout: () => Promise<void>;
  logoutAll: () => Promise<void>;
  clear: () => void;
  setTokensForLab: (tokens: TokenPair | null) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredAuth(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) as StoredAuth : null;
  } catch { return null; }
}

function decodePart(part: string): Record<string, unknown> | null {
  try {
    const normalized = part.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const decoded = decodeURIComponent(Array.from(atob(padded)).map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, '0')}`).join(''));
    const parsed = JSON.parse(decoded);
    return parsed && typeof parsed === 'object' ? parsed : null;
  } catch { return null; }
}

export function decodeJwt(token: string | null): JwtSnapshot {
  if (!token) return { header: null, claims: null };
  const [header, payload] = token.split('.');
  if (!header || !payload) return { header: null, claims: null };
  return { header: decodePart(header), claims: decodePart(payload) };
}

export function AuthProvider({ children }: PropsWithChildren) {
  const initial = useMemo(readStoredAuth, []);
  const [tokens, setTokens] = useState<TokenPair | null>(initial?.tokens ?? null);
  const [email, setEmail] = useState<string | null>(initial?.email ?? null);
  const tokensRef = useRef<TokenPair | null>(tokens);
  const refreshPromise = useRef<Promise<boolean> | null>(null);

  useEffect(() => {
    tokensRef.current = tokens;
    if (tokens) sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ tokens, email } satisfies StoredAuth));
    else sessionStorage.removeItem(STORAGE_KEY);
  }, [tokens, email]);

  const clear = useCallback(() => {
    tokensRef.current = null;
    setTokens(null);
    setEmail(null);
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const refresh = useCallback(async () => {
    if (refreshPromise.current) return refreshPromise.current;
    const current = tokensRef.current;
    if (!current?.refreshToken) return false;

    refreshPromise.current = authApi.refresh(current.refreshToken)
      .then((next) => { tokensRef.current = next; setTokens(next); return true; })
      .catch(() => { clear(); return false; })
      .finally(() => { refreshPromise.current = null; });
    return refreshPromise.current;
  }, [clear]);

  useEffect(() => {
    configureAuthBridge({ getAccessToken: () => tokensRef.current?.accessToken ?? null, refresh });
  }, [refresh]);

  const login = useCallback(async (nextEmail: string, password: string) => {
    const next = await authApi.login(nextEmail, password);
    tokensRef.current = next;
    setEmail(nextEmail.trim().toLowerCase());
    setTokens(next);
  }, []);

  const logout = useCallback(async () => { try { await authApi.logout(); } finally { clear(); } }, [clear]);
  const logoutAll = useCallback(async () => { try { await authApi.logoutAll(); } finally { clear(); } }, [clear]);
  const setTokensForLab = useCallback((next: TokenPair | null) => {
    tokensRef.current = next;
    setTokens(next);
    if (!next) setEmail(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    tokens,
    email,
    jwt: decodeJwt(tokens?.accessToken ?? null),
    authenticated: Boolean(tokens?.accessToken && tokens?.refreshToken),
    login,
    refresh,
    logout,
    logoutAll,
    clear,
    setTokensForLab,
  }), [tokens, email, login, refresh, logout, logoutAll, clear, setTokensForLab]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
