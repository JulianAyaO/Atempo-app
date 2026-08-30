const WebSocket = require('ws');
const http = require('http');

const WS_URL = 'ws://localhost:8080/ws-native';
const SESSION = '35ccadd7-4d93-458c-a8be-d24c82b7e7db';
let msgCount = 0;
const messages = [];

const ws = new WebSocket(WS_URL);

ws.on('open', () => {
  console.log('WS connected');
  ws.send('CONNECT\naccept-version:1.2\nhost:/\n\n\0');
  ws.send('SUBSCRIBE\nid:sub-1\ndestination:/topic/waiters/alerts\n\n\0');
  console.log('Subscribed. Waiting 2s before HTTP call...');

  setTimeout(async () => {
    console.log('--- Calling waiter for table 1 ---');
    await post('/api/orders/sessions/' + SESSION + '/call-waiter', {});
    console.log('HTTP call done. Waiting 5s for WS messages...');
  }, 2000);
});

ws.on('message', (data) => {
  const text = data.toString();
  if (text.includes('MESSAGE')) {
    const lines = text.split('\n');
    let body = '';
    let inBody = false;
    for (const line of lines) {
      if (inBody) body += line + '\n';
      if (line === '') inBody = true;
    }
    body = body.replace(/\0$/, '').trim();
    try {
      const obj = JSON.parse(body);
      msgCount++;
      const key = `${obj.type}-${obj.orderId || obj.tableId || 'none'}-${obj.status || 'none'}`;
      messages.push({ time: Date.now(), key, body });
      console.log(`[${msgCount}] ${key}`);
      console.log('BODY:', JSON.stringify(obj));
    } catch (e) {}
  }
});

ws.on('error', (e) => console.log('WS error:', e.message));
ws.on('close', () => console.log('WS closed'));

function post(path, body) {
  return new Promise((resolve) => {
    const options = { hostname: 'localhost', port: 8080, path, method: 'POST', headers: { 'Content-Type': 'application/json' } };
    const req = http.request(options, (res) => { let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(d)); });
    req.on('error', (e) => resolve('error: ' + e.message));
    req.write(JSON.stringify(body));
    req.end();
  });
}

setTimeout(() => {
  console.log('\n=== WS SUMMARY ===');
  console.log('Total messages:', msgCount);
  ws.close();
  process.exit(msgCount > 0 ? 0 : 1);
}, 8000);
