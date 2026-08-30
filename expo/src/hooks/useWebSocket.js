import { useEffect, useRef, useCallback, useState } from 'react';
import wsClient from '../api/wsClient';
import { useAuthContext } from '../context/AuthContext';

let _consumerCount = 0;

export function useWebSocket() {
  const { token } = useAuthContext();
  const [connected, setConnected] = useState(wsClient.isConnected());
  const cleanupFnsRef = useRef([]);

  useEffect(() => {
    _consumerCount++;
    if (_consumerCount === 1) {
      wsClient.connect(null, null, token);
    }
    const checkInterval = setInterval(() => {
      setConnected(wsClient.isConnected());
    }, 1000);
    return () => {
      clearInterval(checkInterval);
      cleanupFnsRef.current.forEach(fn => fn());
      cleanupFnsRef.current = [];
      _consumerCount--;
      if (_consumerCount <= 0) {
        _consumerCount = 0;
        wsClient.disconnect();
      }
    };
  }, [token]);

  const subscribe = useCallback((topic, callback) => {
    const unsub = wsClient.subscribe(topic, callback);
    cleanupFnsRef.current.push(unsub);
    return unsub;
  }, []);

  const send = useCallback((destination, body) => {
    wsClient.send(destination, body);
  }, []);

  return { connected, subscribe, send };
}
