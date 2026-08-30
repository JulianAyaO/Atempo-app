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
  const tableId = 99;
  console.log(`=== Testing payment + reset flow for table ${tableId} ===`);

  // 1. Create session
  const s1 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('Create session:', s1.status, s1.data?.sessionId || s1.data);
  const sessionId1 = s1.data.sessionId;

  // 2. Create draft order
  const d1 = await req(`/api/orders/sessions/${sessionId1}/draft`, 'POST');
  console.log('Create draft:', d1.status, d1.data?.orderId || d1.data);
  const orderId = d1.data.orderId;

  // 3. Add item
  const add = await req(`/api/orders/${orderId}/items`, 'POST', { productId: 1, quantity: 2 });
  console.log('Add item:', add.status);

  // 4. Confirm order
  const conf = await req(`/api/orders/${orderId}/confirm`, 'POST');
  console.log('Confirm:', conf.status, conf.data?.status);

  // 5. Change to READY
  const ready = await req(`/api/orders/${orderId}/status`, 'PUT', { status: 'READY' });
  console.log('Ready:', ready.status, ready.data?.status);

  // 6. Request payment
  const rp = await req(`/api/orders/sessions/${sessionId1}/request-payment`, 'POST');
  console.log('Request payment:', rp.status, rp.data?.status);

  // 7. Mark PAID
  const paid = await req(`/api/orders/${orderId}/status`, 'PUT', { status: 'PAID' });
  console.log('Paid:', paid.status, paid.data?.status);

  // 8. Verify /current returns 404
  const current1 = await req(`/api/orders/table/${tableId}/current`);
  console.log('Current after PAID:', current1.status, '(should be 404)');

  // 9. Create new session for same table
  const s2 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('New session:', s2.status, s2.data?.sessionId);
  const sessionId2 = s2.data.sessionId;

  // 10. Verify new session is different
  console.log('Session changed:', sessionId1 !== sessionId2, `(old=${sessionId1}, new=${sessionId2})`);

  // 11. Verify /current still 404
  const current2 = await req(`/api/orders/table/${tableId}/current`);
  console.log('Current for new session:', current2.status, '(should be 404)');

  // 12. Verify new draft is empty
  const d2 = await req(`/api/orders/sessions/${sessionId2}/draft`, 'POST');
  console.log('New draft:', d2.status, d2.data?.orderId, 'items:', d2.data?.items?.length || 0);

  console.log('=== DONE ===');
}

run().catch(console.error);
