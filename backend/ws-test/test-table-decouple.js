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
  console.log(`=== Testing table decouple for table ${tableId} ===`);

  // 1. Create session
  const s1 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('1. Create session:', s1.status, JSON.stringify(s1.data));
  if (s1.status !== 200) { console.log('FAIL: could not create session'); return; }
  const sessionId1 = s1.data.sessionId;

  // 2. Create draft order for session
  const d1 = await req(`/api/orders/sessions/${sessionId1}/draft`, 'POST');
  console.log('2. Create draft:', d1.status, JSON.stringify(d1.data));
  const orderId = d1.data.orderId;

  // 3. Add item
  const add = await req(`/api/orders/${orderId}/items`, 'POST', { productId: 1, quantity: 2 });
  console.log('3. Add item:', add.status);

  // 4. Check /current before confirm
  const curBefore = await req(`/api/orders/table/${tableId}/current`);
  console.log('4. /current before confirm:', curBefore.status, curBefore.data?.status || curBefore.data);

  // 5. Confirm order
  const conf = await req(`/api/orders/${orderId}/confirm`, 'POST');
  console.log('5. Confirm:', conf.status, conf.data?.status);

  // 6. Change to READY
  const ready = await req(`/api/orders/${orderId}/status`, 'PUT', { status: 'READY' });
  console.log('6. Ready:', ready.status, ready.data?.status);

  // 7. Request payment
  const rp = await req(`/api/orders/sessions/${sessionId1}/request-payment`, 'POST');
  console.log('7. Request payment:', rp.status, rp.data?.status);

  // 8. Mark PAID
  const paid = await req(`/api/orders/${orderId}/status`, 'PUT', { status: 'PAID' });
  console.log('8. Paid:', paid.status, paid.data?.status);

  // 9. Verify /current returns 404
  const curAfter = await req(`/api/orders/table/${tableId}/current`);
  console.log('9. /current after PAID:', curAfter.status, '(should be 404)');

  // 10. Verify session is CLOSED
  const sessions = await req(`/api/orders/sessions/table/${tableId}`);
  console.log('10. Sessions for table:', sessions.status, JSON.stringify(sessions.data));

  // 11. Create NEW session for same table
  const s2 = await req(`/api/orders/sessions/table/${tableId}`, 'POST');
  console.log('11. New session:', s2.status, JSON.stringify(s2.data));

  // 12. Verify new session is different
  const sessionId2 = s2.data?.sessionId;
  console.log('12. Session changed:', sessionId1 !== sessionId2, `(old=${sessionId1}, new=${sessionId2})`);

  // 13. Verify /current for new session
  const curNew = await req(`/api/orders/table/${tableId}/current`);
  console.log('13. /current for new session:', curNew.status, curNew.data?.status || curNew.data);

  // 14. Verify new draft is empty
  const d2 = await req(`/api/orders/sessions/${sessionId2}/draft`, 'POST');
  console.log('14. New draft:', d2.status, 'items:', d2.data?.items?.length || 0);

  // 15. Check that paid order still exists
  const paidOrder = await req(`/api/orders/${orderId}`);
  console.log('15. Paid order exists:', paidOrder.status, 'status:', paidOrder.data?.status, 'tableId:', paidOrder.data?.tableId);

  console.log('=== DONE ===');
}

run().catch(console.error);
