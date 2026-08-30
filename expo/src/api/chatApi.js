import { SERVER_URL } from './config';

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

async function handleResponse(res) {
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${await res.text()}`);
  return res.json();
}

export async function sendChatMessage(tableId, sessionId, message, idempotencyKey, token, source = null) {
  const body = { tableId, sessionId, message, idempotencyKey };
  if (source) body.source = source;
  const res = await fetch(`${SERVER_URL}/api/chat/turn`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify(body),
  });
  return handleResponse(res);
}

export async function getChatHistory(sessionId, token = null) {
  const res = await fetch(`${SERVER_URL}/api/chat/history/${sessionId}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function clearChatHistory(sessionId, token = null) {
  const res = await fetch(`${SERVER_URL}/api/chat/history/${sessionId}`, {
    method: 'DELETE',
    headers: authHeaders(token),
  });
  return handleResponse(res);
}
