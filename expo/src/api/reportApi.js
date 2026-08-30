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

export async function getSalesReport(period, token) {
  const res = await fetch(`${SERVER_URL}/api/reports/sales?period=${period}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getTopProducts(token) {
  const res = await fetch(`${SERVER_URL}/api/reports/top-products?limit=10`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getOrdersReport(token) {
  const res = await fetch(`${SERVER_URL}/api/reports/orders`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getDashboard(fecha, token) {
  const params = fecha ? `?fecha=${fecha}` : '';
  const res = await fetch(`${SERVER_URL}/api/admin/dashboard${params}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function exportCsv(fecha, token) {
  const params = fecha ? `?fecha=${fecha}` : '';
  const res = await fetch(`${SERVER_URL}/api/admin/reportes/exportar/csv${params}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.blob();
}

export async function exportPdf(fecha, token) {
  const params = fecha ? `?fecha=${fecha}` : '';
  const res = await fetch(`${SERVER_URL}/api/admin/reportes/exportar/pdf${params}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.blob();
}

export function getDashboardExportUrl(type, fecha = null) {
  const params = fecha ? `?fecha=${fecha}` : '';
  const path = type === 'csv' ? 'csv' : 'pdf';
  return `${SERVER_URL}/api/admin/reportes/exportar/${path}${params}`;
}

function isoDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function periodDateRange(period) {
  const hasta = new Date();
  const desde = new Date();
  if (period === 'week') desde.setDate(desde.getDate() - 6);
  else if (period === 'month') desde.setDate(desde.getDate() - 29);
  else {
    desde.setHours(0, 0, 0, 0);
  }
  return { desde: isoDate(desde), hasta: isoDate(hasta) };
}

export function getPeriodExportUrl(type, period = 'today') {
  const { desde, hasta } = periodDateRange(period);
  const path = type === 'csv' ? 'csv-range' : 'pdf-range';
  return `${SERVER_URL}/api/admin/reportes/exportar/${path}?desde=${desde}&hasta=${hasta}`;
}

export async function getActiveTables(token) {
  const res = await fetch(`${SERVER_URL}/api/admin/dashboard/tables`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getLiveDashboard(token) {
  const res = await fetch(`${SERVER_URL}/api/admin/dashboard/live`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function getDateRangeReport(desde, hasta, token) {
  const res = await fetch(`${SERVER_URL}/api/admin/reportes/date-range?desde=${desde}&hasta=${hasta}`, {
    headers: authHeaders(token),
  });
  return handleResponse(res);
}

export async function exportCsvRange(desde, hasta, token) {
  const res = await fetch(`${SERVER_URL}/api/admin/reportes/exportar/csv-range?desde=${desde}&hasta=${hasta}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.blob();
}

export async function exportPdfRange(desde, hasta, token) {
  const res = await fetch(`${SERVER_URL}/api/admin/reportes/exportar/pdf-range?desde=${desde}&hasta=${hasta}`, {
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.blob();
}
