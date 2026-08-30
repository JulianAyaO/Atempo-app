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

export async function getActiveOrders(token) {
  const res = await fetch(`${SERVER_URL}/api/orders/active`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getOrder(orderId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/${orderId}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function changeOrderStatus(orderId, status, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/${orderId}/status`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ status }),
  });
  return handleResponse(res);
}

export async function getPaymentRequested(token) {
  const res = await fetch(`${SERVER_URL}/api/orders/payment-requested`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function createOrder(tableId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ tableId }),
  });
  return handleResponse(res);
}

export async function closeSession(sessionId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/sessions/${sessionId}/close`, {
    method: 'POST',
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function callWaiter(sessionId) {
  const res = await fetch(`${SERVER_URL}/api/orders/sessions/${sessionId}/call-waiter`, {
    method: 'POST',
    headers: authHeaders(null),
  });
  return handleResponse(res);
}

export async function requestPaymentBySession(sessionId, tableId) {
  const res = await fetch(`${SERVER_URL}/api/orders/sessions/${sessionId}/request-payment`, {
    method: 'POST',
    headers: authHeaders(null),
    body: JSON.stringify({ tableId }),
  });
  return handleResponse(res);
}

export async function confirmOrderForSession(orderId, sessionId) {
  const res = await fetch(`${SERVER_URL}/api/orders/${orderId}/confirm`, {
    method: 'POST',
    headers: authHeaders(null),
    body: JSON.stringify({ sessionId }),
  });
  return handleResponse(res);
}

export async function clearDraftItems(orderId, sessionId) {
  const res = await fetch(`${SERVER_URL}/api/orders/${orderId}/clear`, {
    method: 'POST',
    headers: authHeaders(null),
    body: JSON.stringify({ sessionId }),
  });
  return handleResponse(res);
}

export async function cancelItemById(orderId, itemId, quantity = 1, sessionId = null) {
  const res = await fetch(`${SERVER_URL}/api/orders/${orderId}/items/${itemId}/cancel`, {
    method: 'POST',
    headers: authHeaders(null),
    body: JSON.stringify({ quantity, sessionId }),
  });
  return handleResponse(res);
}

export async function getRecentAlerts(token) {
  const res = await fetch(`${SERVER_URL}/api/orders/alerts/recent`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function dismissAlert(type, tableId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/alerts/dismiss`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ type, tableId }),
  });
  return handleResponse(res);
}

export async function getOrdersByTable(tableId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/table/${tableId}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getCurrentTableOrder(tableId) {
  const res = await fetch(`${SERVER_URL}/api/orders/table/${tableId}/current`, {
    headers: authHeaders(null),
  });
  if (res.status === 404) return null;
  return handleResponse(res);
}

export async function resetDraftForTable(tableId, token) {
  const res = await fetch(`${SERVER_URL}/api/orders/table/${tableId}/reset-draft`, {
    method: 'POST',
    headers: authHeaders(token),
  });
  return handleResponse(res);
}
