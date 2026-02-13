import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { authApi, type AuthRequest, type AuthResponse } from '../api/client';

interface UserInfo {
  username: string;
  role: string;
}

interface AuthContextType {
  token: string | null;
  user: UserInfo | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (data: AuthRequest) => Promise<AuthResponse>;
  register: (data: AuthRequest) => Promise<AuthResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

function loadUser(): UserInfo | null {
  const raw = localStorage.getItem('user');
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));
  const [user, setUser] = useState<UserInfo | null>(() => loadUser());

  // Keep localStorage in sync
  useEffect(() => {
    if (token) {
      localStorage.setItem('token', token);
    } else {
      localStorage.removeItem('token');
    }
  }, [token]);

  useEffect(() => {
    if (user) {
      localStorage.setItem('user', JSON.stringify(user));
    } else {
      localStorage.removeItem('user');
    }
  }, [user]);

  const handleAuth = useCallback(async (promise: Promise<{ data: AuthResponse }>) => {
    const { data } = await promise;
    setToken(data.token);
    setUser({ username: data.username, role: data.role });
    return data;
  }, []);

  const login = useCallback(
    (data: AuthRequest) => handleAuth(authApi.login(data)),
    [handleAuth]
  );

  const register = useCallback(
    (data: AuthRequest) => handleAuth(authApi.register(data)),
    [handleAuth]
  );

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  const value: AuthContextType = {
    token,
    user,
    isAuthenticated: !!token,
    isAdmin: user?.role === 'ADMIN',
    login,
    register,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
