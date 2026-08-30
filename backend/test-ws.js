const WebSocket = require('ws');

const WS_URL = 'ws://localhost:8080/ws-native';
const TABLE_ID = 6;

let msgCount = 0;
const msgLog = [];

function connect() {
  const ws = new WebSocket(WS_URL);

  ws.on('open', () => {
    console.log('WS connected');
    // STOMP CONNECT
    ws.send('CONNECT\naccept-version:1.2\nhost:/\n\n\0');
    // Subscribe to table orders
    ws.send(`SUBSCRIBE\nid:sub-1\ndestination:/topic/table/${TABLE_ID}/orders\n\n\0`);
    // Subscribe to waiters alerts
    ws.send('SUBSCRIBE\nid:sub-2\ndestination:/topic/waiters/alerts\n\n\0');
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
        const key = obj.type + (obj.orderId ? '-' + obj.orderId : '') + (obj.status ? '-' + obj.status : '');
        msgLog.push({ time: Date.now(), key, body });
        console.log(`[${msgCount}] ${key}`);
      } catch (e) {
        // ignore
      }
    }
  });

  ws.on('error', (e) => console.log('WS error:', e.message));
  ws.on('close', () => console.log('WS closed'));
}

connect();

// After 30s print summary
setTimeout(() => {
  console.log('\n=== WS TEST SUMMARY ===');
  console.log('Total messages:', msgCount);
  const duplicates = msgLog.filter((item, index, self) =>
    self.findIndex(m => m.key === item.key && Math.abs(m.time - item.time) < 2000) !== index
  );
  console.log('Potential duplicates (<2s):', duplicates.length);
  duplicates.forEach(d => console.log('  dup:', d.key, new Date(d.time).toISOString()));
}, 30000);

// Simulate state changes via HTTP after 5s
setTimeout(() => {
  console.log('\n--- Simulating state changes ---');
  const http = require('http');

  function post(path, body) {
    return new Promise((resolve) => {
      const options = { hostname: 'localhost', port: 8080, path, method: 'POST', headers: { 'Content-Type': 'application/json' } };
      const req = http.request(options, (res) => { resolve(res.statusCode); });
      req.on('error', () => resolve(0));
      req.write(JSON.stringify(body));
      req.end();
    });
  }

  function patch(path, body) {
    return new Promise((resolve) => {
      const options = { hostname: 'localhost', port: 8080, path, method: 'PATCH', headers: { 'Content-Type': 'application/json' } };
      const req = http.request(options, (res) => { resolve(res.statusCode); });
      req.on('error', () => resolve(0));
      req.write(JSON.stringify(body));
      req.end();
    });
  }

  async function run() {
    const session = await post('/api/orders/sessions/table/6', {});
    console.log('Session created:', session);
    const chat = await post('/api/chat/turn', { sessionId: 'test', tableId: 6, message: 'quiero 1 guacamole', idempotencyKey: 'ws1' });
    console.log('Chat:', chat);
  }
  run();
}, 5000);
