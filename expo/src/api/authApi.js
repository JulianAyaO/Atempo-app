import { SERVER_URL } from './config';

const headers = () => ({
  'Content-Type': 'application/json',
});

async function handleResponse(res) {
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
  return res.json();
}

export async function login(name, pin) {
  const res = await fetch(`${SERVER_URL}/api/auth/login`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ name, pin }),
  });
  return handleResponse(res);
}

export async function createSession(tableId) {
  const res = await fetch(`${SERVER_URL}/api/orders/sessions/table/${tableId}`, {
    method: 'POST',
    headers: headers(),
  });
  return handleResponse(res);
}

export async function checkHealth() {
  const res = await fetch(`${SERVER_URL}/api/health`);
  return res.ok;
}
