import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '../services/api';
import { ApiError, onUnauthorized } from '../services/http';
import type { LoginRequest, Profile, RegisterRequest } from '../types/api';

interface AuthState {
  user: Profile | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<Profile>;
  register: (body: RegisterRequest) => Promise<Profile>;
  logout: () => Promise<void>;
  setUser: (user: Profile) => void;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Holds the signed-in student. The session itself lives in an HttpOnly cookie that JavaScript
 * cannot read; on start-up we simply ask the server who we are (GET /auth/me).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    authApi
      .me()
      .then((profile) => active && setUserState(profile))
      .catch((error: unknown) => {
        if (active && !(error instanceof ApiError && error.status === 401)) {
          console.warn('Could not restore the session', error);
        }
      })
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    // Any 401 from the API means the session expired: go back to the login screen.
    onUnauthorized(() => {
      setUserState(null);
      queryClient.clear();
    });
    return () => onUnauthorized(null);
  }, [queryClient]);

  const login = useCallback(
    async (body: LoginRequest) => {
      const profile = await authApi.login(body);
      queryClient.clear();
      setUserState(profile);
      return profile;
    },
    [queryClient],
  );

  const register = useCallback(
    async (body: RegisterRequest) => {
      const profile = await authApi.register(body);
      queryClient.clear();
      setUserState(profile);
      return profile;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      queryClient.clear();
      setUserState(null);
    }
  }, [queryClient]);

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, register, logout, setUser: setUserState }),
    [user, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}

/** The signed-in student; only use inside protected pages. */
export function useUser(): Profile {
  const { user } = useAuth();
  if (!user) throw new Error('No signed-in user');
  return user;
}
