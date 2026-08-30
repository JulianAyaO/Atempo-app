import React, { createContext, useState, useContext, useCallback, useEffect } from 'react';
import * as SecureStore from 'expo-secure-store';

const AuthContext = createContext(null);
const AUTH_STORAGE_KEY = 'restaurant.auth';
const SESSION_STORAGE_KEY = 'restaurant.session';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [session, setSession] = useState(null);
  const [isBootstrapping, setBootstrapping] = useState(true);

  useEffect(() => {
    let mounted = true;
    async function restore() {
      try {
        const [authRaw, sessionRaw] = await Promise.all([
          SecureStore.getItemAsync(AUTH_STORAGE_KEY),
          SecureStore.getItemAsync(SESSION_STORAGE_KEY),
        ]);
        if (!mounted) return;
        if (authRaw) {
          const auth = JSON.parse(authRaw);
          setUser(auth.user ?? null);
          setToken(auth.token ?? null);
        }
        if (sessionRaw) {
          setSession(JSON.parse(sessionRaw));
        }
      } finally {
        if (mounted) setBootstrapping(false);
      }
    }
    restore();
    return () => { mounted = false; };
  }, []);

  const login = useCallback(async (userData, authToken) => {
    setUser(userData);
    setToken(authToken);
    await SecureStore.setItemAsync(AUTH_STORAGE_KEY, JSON.stringify({ user: userData, token: authToken }));
  }, []);

  const logout = useCallback(async () => {
    setUser(null);
    setToken(null);
    setSession(null);
    await Promise.all([
      SecureStore.deleteItemAsync(AUTH_STORAGE_KEY),
      SecureStore.deleteItemAsync(SESSION_STORAGE_KEY),
    ]);
  }, []);

  const startSession = useCallback(async (sessionData) => {
    setSession(sessionData);
    await SecureStore.setItemAsync(SESSION_STORAGE_KEY, JSON.stringify(sessionData));
  }, []);

  const endSession = useCallback(async () => {
    setSession(null);
    await SecureStore.deleteItemAsync(SESSION_STORAGE_KEY);
  }, []);

  return (
    <AuthContext.Provider value={{ user, token, session, isBootstrapping, login, logout, startSession, endSession }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuthContext() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuthContext must be inside AuthProvider');
  return ctx;
}
