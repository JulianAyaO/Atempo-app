import { SERVER_URL } from './config';

class WebSocketClient {
  constructor() {
    this.ws = null;
    this.subscriptions = {};
    this.connected = false;
    this.reconnectTimer = null;
    this.messageId = 0;
    this.authToken = null;
  }

  connect(onConnect, onError, token = null) {
    if (this.connecting) return;
    this.authToken = token;
    this.connecting = true;
    const url = SERVER_URL.replace('https://', 'wss://').replace('http://', 'ws://').replace(/\/$/, '') + '/ws-native';
    try {
      this.ws = new WebSocket(url);
    } catch (e) {
      this.connecting = false;
      console.log('WS: no se pudo conectar (tunnel mode?)');
      return;
    }

    this.ws.onopen = () => {
      this.connected = true;
      this.connecting = false;
      console.log('[WS] onopen — conectado');
      this.sendFrame('CONNECT', this.authHeaders({ 'accept-version': '1.2', host: '/' }));
      // Re-suscribir a todos los topics pendientes
      const topics = Object.keys(this.subscriptions);
      console.log('[WS] Re-suscribiendo a topics:', topics);
      topics.forEach(dest => {
        this.messageId++;
        this.sendFrame('SUBSCRIBE', this.authHeaders({ id: `sub-${this.messageId}`, destination: dest }));
      });
      if (onConnect) onConnect();
    };

    this.ws.onmessage = (event) => {
      console.log('[WS] raw message length:', event.data?.length);
      const frame = this.parseFrame(event.data);
      if (!frame) {
        console.log('[WS] parseFrame devolvio null');
        return;
      }
      console.log('[WS] frame command:', frame.command, 'headers:', JSON.stringify(frame.headers));

      if (frame.command === 'CONNECTED') {
        console.log('[WS] STOMP conectado');
      } else if (frame.command === 'MESSAGE') {
        const dest = frame.headers.destination;
        console.log('[WS] MESSAGE para destination:', dest);
        if (this.subscriptions[dest]) {
          console.log('[WS] callbacks encontrados para', dest, ':', this.subscriptions[dest].length);
          try {
            const body = JSON.parse(frame.body);
            console.log('[WS] body parseado:', JSON.stringify(body).substring(0, 200));
            this.subscriptions[dest].forEach(cb => cb(body));
          } catch (e) {
            console.log('[WS] body no es JSON, enviando raw');
            this.subscriptions[dest].forEach(cb => cb(frame.body));
          }
        } else {
          console.log('[WS] No hay callbacks para destination:', dest);
        }
      }
    };

    this.ws.onerror = (e) => {
      console.log('[WS] onerror:', e.message || e);
      this.connecting = false;
    };

    this.ws.onclose = () => {
      console.log('[WS] onclose — desconectado');
      this.connected = false;
      this.connecting = false;
      this.reconnectTimer = setTimeout(() => this.connect(onConnect, onError, this.authToken), 10000);
    };
  }

  subscribe(destination, callback) {
    console.log('[WS] subscribe a', destination);
    if (!this.subscriptions[destination]) {
      this.subscriptions[destination] = [];
    }
    this.subscriptions[destination].push(callback);

    if (this.connected) {
      this.messageId++;
      console.log('[WS] Enviando SUBSCRIBE para', destination);
      this.sendFrame('SUBSCRIBE', this.authHeaders({
        id: `sub-${this.messageId}`,
        destination: destination,
      }));
    } else {
      console.log('[WS] No conectado aun, subscription pendiente para', destination);
    }
    return () => {
      console.log('[WS] unsubscribe de', destination);
      if (this.subscriptions[destination]) {
        this.subscriptions[destination] = this.subscriptions[destination].filter(cb => cb !== callback);
      }
    };
  }

  send(destination, body) {
    if (!this.connected) return;
    this.sendFrame('SEND', { destination }, JSON.stringify(body));
  }

  sendFrame(command, headers, body = '') {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
    let frame = `${command}\n`;
    Object.entries(headers).forEach(([k, v]) => { frame += `${k}:${v}\n`; });
    frame += `\n${body}\0`;
    this.ws.send(frame);
  }

  authHeaders(headers = {}) {
    return this.authToken ? { ...headers, Authorization: `Bearer ${this.authToken}` } : headers;
  }

  parseFrame(data) {
    if (!data || data === '\n') return null;
    const lines = data.split('\n');
    const command = lines[0];
    const headers = {};
    let i = 1;
    for (; i < lines.length; i++) {
      if (lines[i] === '') break;
      const [k, ...v] = lines[i].split(':');
      headers[k] = v.join(':');
    }
    const body = lines.slice(i + 1).join('\n').replace(/\0$/, '');
    return { command, headers, body };
  }

  disconnect() {
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.ws) {
      this.sendFrame('DISCONNECT', {});
      this.ws.close();
    }
    this.connected = false;
    // NO borrar subscriptions — así al reconectar se re-suscriben automáticamente
  }

  isConnected() {
    return this.connected;
  }
}

export const wsClient = new WebSocketClient();
export default wsClient;
