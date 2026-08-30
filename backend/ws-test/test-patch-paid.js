const BASE = 'http://localhost:8080';

async function req(path, method = 'GET', body = null) {
  const opts = { method, headers: { 'Content-Type': 'application/json' } };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(`${BASE}${path}`, opts);
  if (res.status === 204 || res.status === 404) return { status: res.status };
  const text = await res.text();
  try { return { status: res.status, data: JSON.parse(text) }; } catch { return { status: res.status, data: text }; }
}

async function run() {
  const tableId = 5;
  console.log('=== Test PAID with PATCH ===');

  // Create session
  const s1 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('Session:', s1.status, s1.data?.sessionId);
  const sid = s1.data.sessionId;

  // Draft
  const d1 = await req(`/api/orders/sessions/${sid}/draft`, 'POST');
  console.log('Draft:', d1.status, d1.data?.orderId);
  const oid = d1.data.orderId;

  // Add item
  await req(`/api/orders/${oid}/items`, 'POST', { productId: 1, quantity: 1 });

  // Confirm
  await req(`/api/orders/${oid}/confirm`, 'POST');

  // Ready
  await req(`/api/orders/${oid}/status`, 'PATCH', { status: 'READY' });

  // Request payment
  await req(`/api/orders/sessions/${sid}/request-payment`, 'POST', { tableId });

  // PAID via PATCH
  const paid = await req(`/api/orders/${oid}/status`, 'PATCH', { status: 'PAID' });
  console.log('PAID PATCH:', paid.status, JSON.stringify(paid.data));

  // Check session status via new session call
  const s2 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('New session call:', s2.status, JSON.stringify(s2.data));
  console.log('Session changed:', sid !== s2.data?.sessionId);

  // Check /current
  const cur = await req(`/api/orders/table/${tableId}/current`);
  console.log('/current:', cur.status);

  console.log('=== DONE ===');
}

run().catch(console.error);
