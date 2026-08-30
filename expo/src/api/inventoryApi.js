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

export async function getInventory(token) {
  const res = await fetch(`${SERVER_URL}/api/inventory`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getInventoryAlerts(token) {
  const res = await fetch(`${SERVER_URL}/api/inventory/alerts`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function restockIngredient(ingredientId, quantity, token) {
  const res = await fetch(`${SERVER_URL}/api/inventory/${ingredientId}/restock`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ quantity }),
  });
  return handleResponse(res);
}

export async function setIngredientStock(ingredientId, quantity, token) {
  const res = await fetch(`${SERVER_URL}/api/inventory/${ingredientId}/set-stock`, {
    method: 'PATCH',
    headers: authHeaders(token),
    body: JSON.stringify({ quantity }),
  });
  return handleResponse(res);
}

export async function markIngredientUnavailable(ingredientId, token) {
  const res = await fetch(`${SERVER_URL}/api/inventory/${ingredientId}/set-unavailable`, {
    method: 'PATCH',
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getMenuWithStock(token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/menu-with-stock`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}
