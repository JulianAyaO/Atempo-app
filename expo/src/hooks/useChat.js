import { useState, useCallback } from 'react';
import { sendChatMessage, getChatHistory } from '../api/chatApi';

export function useChat(tableId, sessionId, token) {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(false);

  const sendMessage = useCallback(async (text, idempotencyKey) => {
    setLoading(true);
    try {
      const res = await sendChatMessage(tableId, sessionId, text, idempotencyKey, token);
      setMessages(prev => [...prev, { role: 'user', content: text }, { role: 'assistant', content: res.message }]);
      return res;
    } finally {
      setLoading(false);
    }
  }, [tableId, sessionId, token]);

  const loadHistory = useCallback(async () => {
    if (!sessionId) return;
    const res = await getChatHistory(sessionId, token);
    setMessages(res.map(t => ({ role: t.role === 'USER' ? 'user' : 'assistant', content: t.content })));
    return res;
  }, [sessionId, token]);

  const sendTurn = useCallback((text, idempotencyKey, source = null) => {
    return sendChatMessage(tableId, sessionId, text, idempotencyKey, token, source);
  }, [tableId, sessionId, token]);

  const loadTurns = useCallback(() => {
    if (!sessionId) return Promise.resolve([]);
    return getChatHistory(sessionId, token);
  }, [sessionId, token]);

  return { messages, loading, sendMessage, sendTurn, loadHistory, loadTurns, setMessages };
}
