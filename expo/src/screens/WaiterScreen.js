import React, { useState, useEffect, useCallback, useRef } from 'react';
import { View, Text, TouchableOpacity, FlatList, StyleSheet, Alert, RefreshControl, Vibration } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthContext } from '../context/AuthContext';
import { getActiveOrders, getPaymentRequested, changeOrderStatus, closeSession, getRecentAlerts, dismissAlert as dismissAlertApi } from '../api/orderApi';
import { useWebSocket } from '../hooks/useWebSocket';
import { formatCOP } from '../utils/currency';
import { formatItemName } from '../utils/orderFormatting';
import ConnectionIndicator from '../components/ConnectionIndicator';
import { POLLING_INTERVALS } from '../constants/appConstants';
import { STATUS_LABELS } from '../constants/orderStatus';

function alertKey(a) {
  return `${a.type}-${a.tableId}-${a.orderId ?? 'none'}-${a.timestamp ?? ''}`;
}

export default function WaiterScreen() {
  const { token } = useAuthContext();
  const { subscribe, connected } = useWebSocket();
  const [alerts, setAlerts] = useState([]);
  const [readyOrders, setReadyOrders] = useState([]);
  const [paymentOrders, setPaymentOrders] = useState([]);
  const [allOrders, setAllOrders] = useState([]);
  const [refreshing, setRefreshing] = useState(false);
  const [tab, setTab] = useState('ready'); // ready | attention | tables | payment
  const dismissedKeysRef = useRef(new Set());

  useEffect(() => {
    loadData();
    const sub1 = subscribe('/topic/waiters/alerts', (msg) => {
      const key = `${msg.type}-${msg.tableId}-${msg.orderId ?? 'none'}-${msg.timestamp ?? ''}`;
      if (dismissedKeysRef.current.has(key)) return; // ya fue descartada
      setAlerts(prev => {
        const exists = prev.some(a => alertKey(a) === key);
        if (exists) return prev;
        if (msg.type === 'WAITER_CALLED') {
          Vibration.vibrate([0, 500, 200, 500]);
        }
        return [{ ...msg, id: Date.now() }, ...prev.slice(0, 49)];
      });
      loadData();
    });
    const sub2 = subscribe('/topic/kitchen/orders', () => {
      loadData();
    });
    const interval = setInterval(loadData, POLLING_INTERVALS.WAITER_DATA_MS);

    // Fallback HTTP polling para alertas (WebSocket no siempre funciona en móviles)
    const pollAlerts = async () => {
      try {
        const recent = await getRecentAlerts(token);
        if (Array.isArray(recent) && recent.length > 0) {
          setAlerts(prev => {
            const existingKeys = new Set(prev.map(alertKey));
            const newAlerts = recent
              .filter(a => !dismissedKeysRef.current.has(alertKey(a)) && !existingKeys.has(alertKey(a)))
              .map(a => ({ ...a, id: Date.now() + Math.random() }));
            if (newAlerts.some(a => a.type === 'WAITER_CALLED')) {
              Vibration.vibrate([0, 500, 200, 500]);
            }
            return [...newAlerts, ...prev].slice(0, 50);
          });
        }
      } catch (e) { /* silenciar errores de polling */ }
    };
    const pollInterval = setInterval(pollAlerts, POLLING_INTERVALS.WAITER_ALERTS_MS);
    pollAlerts(); // primera carga inmediata

    return () => { clearInterval(interval); clearInterval(pollInterval); if (sub1) sub1(); if (sub2) sub2(); };
  }, [subscribe, token]);

  const loadData = async () => {
    try {
      const active = await getActiveOrders(token);
      const safeActive = Array.isArray(active) ? active : [];
      const payments = await getPaymentRequested(token);
      const safePayments = Array.isArray(payments) ? payments : [];
      setPaymentOrders(safePayments);
      // Merge active + payment orders for Mesas tab (avoid duplicates by orderId)
      const mergedMap = new Map();
      for (const o of safeActive) mergedMap.set(o.orderId, o);
      for (const o of safePayments) mergedMap.set(o.orderId, o);
      const merged = Array.from(mergedMap.values());
      setAllOrders(merged);
      setReadyOrders(safeActive.filter(o => o.status === 'READY'));
      // Auto-dismiss stale alerts + notify backend
      setAlerts(prev => {
        const stale = prev.filter(a => {
          if (a.type === 'ORDER_READY') {
            const order = merged.find(o => String(o.orderId) === String(a.orderId));
            return !order || order.status !== 'READY';
          }
          if (a.type === 'PAYMENT_REQUESTED') {
            const order = merged.find(o => String(o.orderId) === String(a.orderId));
            return !order || order.status !== 'PAYMENT_REQUESTED';
          }
          return false;
        });
        // Notify backend to permanently remove stale alerts
        stale.forEach(a => {
          dismissedKeysRef.current.add(alertKey(a));
          if (a.tableId) {
            dismissAlertApi(a.type, a.tableId, token).catch(() => {});
          }
        });
        // Keep only non-stale alerts
        const fresh = prev.filter(a => {
          if (a.type === 'ORDER_READY') {
            const order = merged.find(o => String(o.orderId) === String(a.orderId));
            return order && order.status === 'READY';
          }
          if (a.type === 'PAYMENT_REQUESTED') {
            const order = merged.find(o => String(o.orderId) === String(a.orderId));
            return order && order.status === 'PAYMENT_REQUESTED';
          }
          return true;
        });
        return fresh;
      });
      return true;
    } catch (e) {
      console.warn('[Waiter] loadData:', e.message);
      return false;
    }
  };

  const tables = React.useMemo(() => {
    const map = new Map();
    for (const o of allOrders) {
      const key = o.tableId || '?';
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(o);
    }
    return Array.from(map.entries()).sort((a, b) => a[0] - b[0]);
  }, [allOrders]);

  const onRefresh = async () => {
    setRefreshing(true);
    const ok = await loadData();
    setRefreshing(false);
    if (!ok) Alert.alert('Conexión', 'No se pudieron actualizar los pedidos. Intenta de nuevo.');
  };

  const handleDeliver = async (orderId) => {
    try {
      await changeOrderStatus(orderId, 'DELIVERED', token);
      setAlerts(prev => prev.filter(a => !(a.type === 'ORDER_READY' && String(a.orderId) === String(orderId))));
      loadData();
    } catch (e) { Alert.alert('Error', e.message); }
  };

  const handlePayment = async (orderId) => {
    try {
      const paid = await changeOrderStatus(orderId, 'PAID', token);
      setAlerts(prev => prev.filter(a => !(a.type === 'PAYMENT_REQUESTED' && String(a.orderId) === String(orderId))));
      setPaymentOrders(prev => prev.filter(o => String(o.orderId) !== String(orderId)));
      setReadyOrders(prev => prev.filter(o => String(o.orderId) !== String(orderId)));
      setAllOrders(prev => prev.filter(o => String(o.tableId) !== String(paid.tableId)));
      loadData();
    } catch (e) { Alert.alert('Error', e.message); }
  };

  const handleFinalizeService = async (sessionId, tableId) => {
    Alert.alert(
      'Finalizar Servicio',
      `¿Seguro que deseas cerrar el servicio de Mesa ${tableId}? La mesa quedará libre para nuevos clientes.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Finalizar',
          style: 'destructive',
          onPress: async () => {
            try {
              await closeSession(sessionId, token);
              setAlerts(prev => prev.filter(a => String(a.tableId) !== String(tableId)));
              Alert.alert('✅ Servicio finalizado', `Mesa ${tableId} liberada y lista para nuevos clientes.`);
              loadData();
            } catch (e) { Alert.alert('Error', e.message); }
          },
        },
      ]
    );
  };

  const dismissAlert = async (id) => {
    const alert = alerts.find(a => a.id === id);
    if (alert) {
      dismissedKeysRef.current.add(alertKey(alert));
      if (alert.tableId) {
        try { await dismissAlertApi(alert.type, alert.tableId, token); } catch (e) { /* silenciar */ }
      }
    }
    setAlerts(prev => prev.filter(a => a.id !== id));
  };

  const markAttended = async (id) => {
    const alert = alerts.find(a => a.id === id);
    if (alert) {
      try {
        await dismissAlertApi(alert.type, alert.tableId, token);
      } catch (e) { /* silenciar */ }
    }
    setAlerts(prev => prev.filter(a => a.id !== id));
  };

  const clearAllAlerts = async () => {
    const toDismiss = [...alerts];
    setAlerts([]);
    for (const a of toDismiss) {
      dismissedKeysRef.current.add(alertKey(a));
      if (a.tableId) {
        try { await dismissAlertApi(a.type, a.tableId, token); } catch (e) { /* silenciar */ }
      }
    }
  };

  const waiterCallAlerts = alerts.filter(a => a.type === 'WAITER_CALLED');

  const renderAttention = ({ item: a }) => (
    <View style={s.attentionCard}>
      <View style={s.attentionRow}>
        <Ionicons name="chatbubble-ellipses" size={28} color="#ef4444" />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.attentionTable}>Mesa {a.tableId}</Text>
          <Text style={s.attentionMsg} numberOfLines={3}>{a.message}</Text>
        </View>
        <TouchableOpacity style={s.attendedBtn} onPress={() => markAttended(a.id)}>
          <Ionicons name="checkmark" size={16} color="#fff" />
          <Text style={s.attendedText}>Atendido</Text>
        </TouchableOpacity>
      </View>
    </View>
  );

  const renderReady = useCallback(({ item }) => (
    <View style={s.card}>
      <View style={s.cardRow}>
        <View style={{ flex: 1, paddingRight: 8 }}>
          <Text style={s.cardTable}>Mesa {item.tableId || '?'}</Text>
          <Text style={s.cardId}>Pedido #{item.orderId}</Text>
          <Text style={s.cardItems} numberOfLines={2}>{item.items?.length || 0} items · {formatCOP(item.total)}</Text>
        </View>
        {item.status === 'READY' && (
          <TouchableOpacity style={s.deliverBtn} onPress={() => handleDeliver(item.orderId)}>
            <Ionicons name="checkmark-done" size={18} color="#fff" />
            <Text style={s.deliverText}>Entregar</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  ), []);

  const renderTable = ({ item: [tableId, orders] }) => {
    const tableAlerts = alerts.filter(a => String(a.tableId) === String(tableId));
    const hasDoubt = tableAlerts.some(a => a.type === 'WAITER_CALLED');
    const hasPayReq = orders.some(o => o.status === 'PAYMENT_REQUESTED') || tableAlerts.some(a => a.type === 'PAYMENT_REQUESTED');
    return (
      <View style={s.tableCard}>
        <View style={s.tableHeaderRow}>
          <Text style={s.tableTitle}>Mesa {tableId}</Text>
          <View style={s.tableBadges}>
            {hasDoubt && (
              <View style={[s.tBadge, s.tBadgeDoubt]}>
                <Ionicons name="chatbubble-ellipses" size={12} color="#fff" />
                <Text style={s.tBadgeText}>Dudas</Text>
              </View>
            )}
            {hasPayReq && (
              <View style={[s.tBadge, s.tBadgePay]}>
                <Ionicons name="cash" size={12} color="#fff" />
                <Text style={s.tBadgeText}>Cuenta</Text>
              </View>
            )}
          </View>
        </View>
        {orders.map((o) => (
          <View key={o.orderId} style={s.tableOrderRow}>
            <View style={{ flex: 1, minWidth: 0, paddingRight: 8 }}>
              <Text style={s.tableOrderId}>Pedido #{o.orderId}</Text>
              <Text style={[s.tableOrderStatus, o.status === 'PAYMENT_REQUESTED' && { color: '#a855f7', fontWeight: '700' }]} numberOfLines={2}>
                {STATUS_LABELS[o.status] || o.status}
              </Text>
              <Text style={s.tableOrderItems} numberOfLines={2}>{o.items?.length || 0} items · {formatCOP(o.total)}</Text>
            </View>
            {o.status === 'READY' && (
              <TouchableOpacity style={s.deliverBtn} onPress={() => handleDeliver(o.orderId)}>
                <Ionicons name="checkmark-done" size={16} color="#fff" />
                <Text style={s.deliverText}>Entregar</Text>
              </TouchableOpacity>
            )}
            {o.status === 'PAYMENT_REQUESTED' && (
              <TouchableOpacity style={s.payBtn} onPress={() => handlePayment(o.orderId)}>
                <Ionicons name="cash" size={16} color="#fff" />
                <Text style={s.payText}>Cobrar</Text>
              </TouchableOpacity>
            )}
            {o.status === 'PAID' && (
              <TouchableOpacity style={s.finalizeBtn} onPress={() => handleFinalizeService(o.sessionId, o.tableId)}>
                <Ionicons name="flag" size={16} color="#fff" />
                <Text style={s.finalizeText}>Finalizar</Text>
              </TouchableOpacity>
            )}
          </View>
        ))}
      </View>
    );
  };

  const renderPayment = useCallback(({ item }) => (
    <View style={[s.card, s.paymentCard]}>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={s.cardTable}>Mesa {item.tableId || '?'}</Text>
          <Text style={s.cardId}>Pedido #{item.orderId}</Text>
          {(item.items || []).map((it, i) => (
            <Text key={i} style={s.cardItems} numberOfLines={2}>{it.quantity}x {formatItemName(it)}</Text>
          ))}
          <Text style={[s.cardTotal, { marginTop: 6 }]}>Total: {formatCOP(item.total)}</Text>
        </View>
        <TouchableOpacity style={s.payBtn} onPress={() => handlePayment(item.orderId)}>
          <Ionicons name="cash" size={18} color="#fff" />
          <Text style={s.payText}>Cobrar</Text>
        </TouchableOpacity>
      </View>
    </View>
  ), []);

  return (
    <View style={s.container}>
      {/* Tabs */}
      <View style={s.tabs}>
        <TouchableOpacity style={[s.tab, tab === 'ready' && s.tabActive]} onPress={() => setTab('ready')}>
          <Ionicons name="checkmark-done" size={18} color={tab === 'ready' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'ready' && s.tabTextActive]} numberOfLines={1}>Listos</Text>
          {readyOrders.length > 0 && (
            <Text style={[s.tabCount, tab === 'ready' && s.tabCountActive]}>{readyOrders.length}</Text>
          )}
        </TouchableOpacity>
        <TouchableOpacity style={[s.tab, tab === 'attention' && s.tabActive]} onPress={() => setTab('attention')}>
          <View style={s.tabIconWrap}>
            <Ionicons name="chatbubble-ellipses" size={18} color={tab === 'attention' ? '#f59e0b' : '#94a3b8'} />
            {waiterCallAlerts.length > 0 && (
              <View style={s.tabDot}><Text style={s.tabDotText}>{waiterCallAlerts.length}</Text></View>
            )}
          </View>
          <Text style={[s.tabText, tab === 'attention' && s.tabTextActive]} numberOfLines={1}>Atención</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.tab, tab === 'tables' && s.tabActive]} onPress={() => setTab('tables')}>
          <Ionicons name="grid-outline" size={18} color={tab === 'tables' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'tables' && s.tabTextActive]} numberOfLines={1}>Mesas</Text>
          {tables.length > 0 && (
            <Text style={[s.tabCount, tab === 'tables' && s.tabCountActive]}>{tables.length}</Text>
          )}
        </TouchableOpacity>
        <TouchableOpacity style={[s.tab, tab === 'payment' && s.tabActive]} onPress={() => setTab('payment')}>
          <Ionicons name="cash-outline" size={18} color={tab === 'payment' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'payment' && s.tabTextActive]} numberOfLines={1}>Cuentas</Text>
          {paymentOrders.length > 0 && (
            <Text style={[s.tabCount, tab === 'payment' && s.tabCountActive]}>{paymentOrders.length}</Text>
          )}
        </TouchableOpacity>
      </View>

      {/* Connection + Alert Banner */}
      <View style={s.topBar}>
        <ConnectionIndicator connected={connected} />
        {alerts.length > 0 && (
          <TouchableOpacity style={s.alertBadge} onPress={clearAllAlerts}>
            <Ionicons name="notifications" size={16} color="#fff" />
            <Text style={s.alertBadgeText}>{alerts.length}</Text>
          </TouchableOpacity>
        )}
      </View>

      {alerts.filter(a => a.type !== 'WAITER_CALLED').length > 0 && (
        <View style={s.alertList}>
          {alerts.filter(a => a.type !== 'WAITER_CALLED').map(a => {
            const isPayment = a.type === 'PAYMENT_REQUESTED';
            const icon = isPayment ? 'cash' : 'notifications';
            const color = isPayment ? '#a855f7' : '#f59e0b';
            return (
              <View key={a.id} style={[s.alertRow, isPayment && s.alertRowPayment]}>
                <Ionicons name={icon} size={18} color={color} />
                <Text style={s.alertRowText} numberOfLines={2}>{a.message}</Text>
                <TouchableOpacity onPress={() => dismissAlert(a.id)}>
                  <Ionicons name="close-circle" size={20} color="#64748b" />
                </TouchableOpacity>
              </View>
            );
          })}
        </View>
      )}

      <FlatList
        data={tab === 'ready' ? readyOrders : tab === 'attention' ? waiterCallAlerts : tab === 'tables' ? tables : paymentOrders}
        renderItem={tab === 'ready' ? renderReady : tab === 'attention' ? renderAttention : tab === 'tables' ? renderTable : renderPayment}
        keyExtractor={(item) => tab === 'tables' ? String(item[0]) : tab === 'attention' ? String(item.id) : String(item.orderId)}
        extraData={alerts}
        contentContainerStyle={s.list}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}
        ListEmptyComponent={
          <View style={s.empty}>
            <Ionicons
              name={tab === 'ready' ? 'checkmark-done-circle-outline' : tab === 'attention' ? 'chatbubble-ellipses-outline' : tab === 'tables' ? 'grid-outline' : 'wallet-outline'}
              size={48}
              color="#334155"
            />
            <Text style={s.emptyText}>
              {tab === 'ready' ? 'No hay pedidos listos'
                : tab === 'attention' ? 'Sin llamadas de atención'
                : tab === 'tables' ? 'No hay pedidos activos'
                : 'No hay cuentas pendientes'}
            </Text>
          </View>
        }
      />
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f1a' },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#1e1e35', backgroundColor: '#12121f' },
  tab: { flex: 1, paddingVertical: 10, alignItems: 'center', gap: 2 },
  tabActive: { borderBottomWidth: 3, borderBottomColor: '#f59e0b' },
  tabText: { fontSize: 11, color: '#94a3b8', fontWeight: '700', textAlign: 'center' },
  tabTextActive: { color: '#f59e0b' },
  tabCount: { fontSize: 11, color: '#64748b', fontWeight: '700' },
  tabCountActive: { color: '#f59e0b' },
  tabIconWrap: { position: 'relative' },
  tabDot: { position: 'absolute', top: -6, right: -10, backgroundColor: '#ef4444', borderRadius: 8, minWidth: 16, height: 16, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 3 },
  tabDotText: { color: '#fff', fontSize: 10, fontWeight: '800' },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 8, backgroundColor: '#0f0f1a', borderBottomWidth: 1, borderBottomColor: '#1e1e35' },
  connIndicator: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  connDot: { width: 8, height: 8, borderRadius: 4 },
  connOn: { backgroundColor: '#22c55e' },
  connOff: { backgroundColor: '#ef4444' },
  connText: { color: '#94a3b8', fontSize: 12 },
  alertBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: '#ef4444', borderRadius: 12, paddingHorizontal: 8, paddingVertical: 4 },
  alertBadgeText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  alertList: { backgroundColor: '#1a1a2e', borderBottomWidth: 1, borderBottomColor: '#2d2d44', paddingHorizontal: 12, paddingVertical: 6, gap: 6 },
  alertRow: { flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: '#0f0f1a', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 10, borderLeftWidth: 3, borderLeftColor: '#f59e0b' },
  alertRowUrgent: { borderLeftColor: '#ef4444', backgroundColor: '#2d1515' },
  alertRowText: { color: '#e2e8f0', fontSize: 14, flex: 1 },
  attendedBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, backgroundColor: '#22c55e', borderRadius: 10, paddingHorizontal: 10, paddingVertical: 8, flexShrink: 0 },
  attendedText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  list: { padding: 16, gap: 12, paddingBottom: 24 },
  card: { backgroundColor: '#1a1a2e', borderRadius: 16, padding: 16, borderLeftWidth: 4, borderLeftColor: '#22c55e' },
  paymentCard: { borderLeftColor: '#a855f7' },
  cardRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  cardTable: { fontSize: 20, fontWeight: '800', color: '#f59e0b' },
  cardId: { fontSize: 13, color: '#94a3b8', marginTop: 2 },
  cardItems: { fontSize: 14, color: '#94a3b8', marginTop: 4 },
  cardTotal: { fontSize: 18, fontWeight: '700', color: '#e2e8f0', marginTop: 4 },
  deliverBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#22c55e', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, flexShrink: 0 },
  deliverText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  payBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#a855f7', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, flexShrink: 0 },
  payText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  finalizeBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#ef4444', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 10, flexShrink: 0 },
  finalizeText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  empty: { alignItems: 'center', justifyContent: 'center', paddingVertical: 80, gap: 12 },
  emptyText: { fontSize: 16, color: '#94a3b8', textAlign: 'center' },
  tableCard: { backgroundColor: '#1a1a2e', borderRadius: 16, padding: 16, marginBottom: 12, borderLeftWidth: 4, borderLeftColor: '#3b82f6' },
  tableHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, gap: 8 },
  tableTitle: { fontSize: 20, fontWeight: '800', color: '#3b82f6', flexShrink: 1 },
  tableBadges: { flexDirection: 'row', gap: 6, flexShrink: 0 },
  tBadge: { flexDirection: 'row', alignItems: 'center', gap: 4, borderRadius: 10, paddingHorizontal: 8, paddingVertical: 4 },
  tBadgeDoubt: { backgroundColor: '#ef4444' },
  tBadgePay: { backgroundColor: '#a855f7' },
  tBadgeText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  tableOrderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#2d2d44', gap: 8 },
  tableOrderId: { fontSize: 14, color: '#e2e8f0', fontWeight: '600' },
  tableOrderStatus: { fontSize: 12, color: '#f59e0b', marginTop: 2 },
  tableOrderItems: { fontSize: 13, color: '#94a3b8', marginTop: 2 },
  alertRowPayment: { borderLeftColor: '#a855f7', backgroundColor: '#1a102e' },
  attentionCard: { backgroundColor: '#2d1515', borderRadius: 16, padding: 16, borderLeftWidth: 4, borderLeftColor: '#ef4444' },
  attentionRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  attentionTable: { fontSize: 18, fontWeight: '800', color: '#ef4444' },
  attentionMsg: { fontSize: 14, color: '#fca5a5', marginTop: 4 },
  attentionTime: { fontSize: 12, color: '#94a3b8', marginTop: 4 },
});
