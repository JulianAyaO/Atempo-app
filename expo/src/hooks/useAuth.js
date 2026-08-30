import { useCallback } from 'react';
import { useAuthContext } from '../context/AuthContext';
import { login as apiLogin, createSession } from '../api/authApi';

export function useAuth() {
  const { user, token, isBootstrapping, login, logout, startSession, endSession } = useAuthContext();

  const signIn = useCallback(async (name, pin) => {
    const res = await apiLogin(name, pin);
    await login({ name: res.name, role: res.role, staffId: res.staffId }, res.token);
    return res;
  }, [login]);

  const signOut = useCallback(async () => {
    await logout();
  }, [logout]);

  const createTableSession = useCallback(async (tableId) => {
    const res = await createSession(tableId);
    await startSession(res);
    return res;
  }, [startSession]);

  return { user, token, isBootstrapping, isAuthenticated: !!token, signIn, signOut, createTableSession, startSession, endSession };
}
