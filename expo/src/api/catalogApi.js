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

export async function getCategories(token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/categories`, { headers: authHeaders(token) });
  return handleResponse(res);
}

export async function createCategory(data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/categories`, {
    method: 'POST', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function updateCategory(id, data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/categories/${id}`, {
    method: 'PUT', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function deleteCategory(id, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/categories/${id}`, {
    method: 'DELETE', headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getIngredients(token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/ingredients`, { headers: authHeaders(token) });
  return handleResponse(res);
}

export async function createIngredient(data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/ingredients`, {
    method: 'POST', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function updateIngredient(id, data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/ingredients/${id}`, {
    method: 'PUT', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function deleteIngredient(id, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/ingredients/${id}`, {
    method: 'DELETE', headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getProducts(token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/products`, { headers: authHeaders(token) });
  return handleResponse(res);
}

export async function createProduct(data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/products`, {
    method: 'POST', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function updateProduct(id, data, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/products/${id}`, {
    method: 'PUT', headers: authHeaders(token), body: JSON.stringify(data),
  });
  return handleResponse(res);
}

export async function deleteProduct(id, token) {
  const res = await fetch(`${SERVER_URL}/api/catalog/products/${id}`, {
    method: 'DELETE', headers: authHeaders(token),
  });
  return handleResponse(res);
}
