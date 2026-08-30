import React, { useState, useEffect, useCallback } from 'react';
import { View, Text, TouchableOpacity, FlatList, StyleSheet, Alert, RefreshControl, Modal, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthContext } from '../context/AuthContext';
import { getActiveOrders, changeOrderStatus } from '../api/orderApi';
import { getInventory, markIngredientUnavailable, getMenuWithStock } from '../api/inventoryApi';
import { useWebSocket } from '../hooks/useWebSocket';
import { STATUS_COLORS, STATUS_LABELS, KITCHEN_NEXT_STATUS as NEXT_STATUS } from '../constants/orderStatus';
import { POLLING_INTERVALS } from '../constants/appConstants';
import { formatItemName } from '../utils/orderFormatting';
import ConnectionIndicator from '../components/ConnectionIndicator';
const SHORT_LABELS = {
  PENDING: 'Pendiente', IN_PREPARATION: 'En preparación',
  READY: 'Listo', DELIVERED: 'Entregado',
};

export default function KitchenScreen() {
  const { token } = useAuthContext();
  const { subscribe, connected } = useWebSocket();
  const [orders, setOrders] = useState([]);
  const [inventory, setInventory] = useState([]);
  const [refreshing, setRefreshing] = useState(false);
  const [tab, setTab] = useState('orders'); // orders | menu | inventory
  const [menuData, setMenuData] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [productModalVisible, setProductModalVisible] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const timer = setInterval(() => setTick(t => t + 1), POLLING_INTERVALS.RELATIVE_TIME_MS);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    loadOrders();
    loadInventory();
    loadMenu();
    const sub1 = subscribe('/topic/kitchen/orders', () => loadOrders());
    const sub2 = subscribe('/topic/kitchen/inventory', () => { loadInventory(); loadMenu(); });
    const sub3 = subscribe('/topic/menu/updates', () => loadMenu());
    const interval = setInterval(() => { loadOrders(); loadInventory(); loadMenu(); }, POLLING_INTERVALS.KITCHEN_DATA_MS);
    return () => { clearInterval(interval); if (sub1) sub1(); if (sub2) sub2(); if (sub3) sub3(); };
  }, [subscribe]);

  const loadOrders = async () => {
    try {
      const data = await getActiveOrders(token);
      setOrders(data);
      return true;
    } catch (e) {
      console.warn('[Kitchen] loadOrders:', e.message);
      return false;
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    const results = await Promise.all([loadOrders(), loadInventory(), loadMenu()]);
    setRefreshing(false);
    if (results.some(ok => !ok)) Alert.alert('Conexión', 'No se pudo actualizar toda la información. Intenta de nuevo.');
  };

  const loadMenu = async () => {
    try {
      const data = await getMenuWithStock(token);
      setMenuData(data);
      return true;
    } catch (e) {
      console.warn('[Kitchen] loadMenu:', e.message);
      return false;
    }
  };

  const loadInventory = async () => {
    try {
      const data = await getInventory(token);
      setInventory(data);
      return true;
    } catch (e) {
      console.warn('[Kitchen] loadInventory:', e.message);
      return false;
    }
  };

  const handleToggleIngredientStock = async (ingredientId, name, isCurrentlyAvailable) => {
    if (!isCurrentlyAvailable) {
      Alert.alert('Sin stock', 'Este ingrediente ya fue reportado. Solo Admin puede reponer inventario.');
      return;
    }
    try {
      await markIngredientUnavailable(ingredientId, token);
      loadInventory();
      loadMenu();
      Alert.alert('Reportado', `${name} fue marcado sin stock. Admin recibirá la alerta para reponer.`);
    } catch (e) { Alert.alert('Error', e.message); }
  };

  const handleToggleProductAvailability = async (product) => {
    const isAvailable = product.active;
    if (!isAvailable) {
      Alert.alert('No disponible', 'Solo Admin puede reponer productos. Cocina puede reportar faltantes.');
      return;
    }
    Alert.alert(
      '⛔ Reportar sin stock',
      `¿Deseas reportar "${product.name}" como no disponible? Admin recibirá la alerta para reponer.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Confirmar',
          onPress: async () => {
            const ingredients = product.ingredients || [];
            const targets = ingredients.filter(i => i.type === 'BASE' || ingredients.every(x => x.type !== 'BASE'));
            if (targets.length === 0) { Alert.alert('Sin ingredientes', 'Este producto no tiene ingredientes configurados.'); return; }
            try {
              await Promise.all(targets.map(ing => {
                return markIngredientUnavailable(ing.id, token);
              }));
              loadMenu();
              loadInventory();
            } catch (e) { Alert.alert('Error', e.message); }
          },
        },
      ]
    );
  };

  const openProductDetail = (product) => {
    setSelectedProduct(product);
    setProductModalVisible(true);
  };

  const handleStatusChange = async (orderId, newStatus) => {
    try {
      await changeOrderStatus(orderId, newStatus, token);
      loadOrders();
    } catch (e) {
      Alert.alert('Error', e.message);
    }
  };

  const renderOrder = useCallback(({ item }) => {
    const next = NEXT_STATUS[item.status];
    const createdMs = item.createdAt ? new Date(item.createdAt).getTime() : null;
    const elapsed = createdMs && !isNaN(createdMs) ? Math.round((Date.now() - createdMs) / 60000) : null;

    return (
      <View style={[s.card, { borderLeftColor: STATUS_COLORS[item.status] || '#64748b' }]}>
        <View style={s.cardHeader}>
          <View style={{ alignItems: 'flex-start', flex: 1 }}>
            <Text style={s.tableLabel}>Mesa {item.tableId || '?'}</Text>
            <Text style={s.orderId}>Pedido #{item.orderId}</Text>
          </View>
          <View style={[s.statusBadge, { backgroundColor: (STATUS_COLORS[item.status] || '#64748b') + '33' }]}>
            <Text style={[s.statusText, { color: STATUS_COLORS[item.status] || '#64748b' }]} numberOfLines={2}>
              {SHORT_LABELS[item.status] || item.status}
            </Text>
          </View>
        </View>

        <View style={s.itemsList}>
          {item.items?.map((it, i) => (
            <View key={i} style={s.itemRow}>
              <Text style={s.itemQty}>{it.quantity}x</Text>
              <Text style={s.itemName} numberOfLines={3}>{formatItemName(it)}</Text>
            </View>
          ))}
        </View>

        <View style={s.cardFooter}>
          <View style={s.timeWrap}>
            <Ionicons name="time-outline" size={16} color="#94a3b8" />
            <Text style={s.timeLabel}>{elapsed !== null ? `${elapsed} min` : 'Sin hora'}</Text>
          </View>
          {next && (
            <TouchableOpacity style={[s.actionBtn, { backgroundColor: STATUS_COLORS[next] }]}
              onPress={() => handleStatusChange(item.orderId, next)}>
              <Ionicons name={next === 'IN_PREPARATION' ? 'flame-outline' : 'checkmark-circle'} size={18} color="#fff" />
              <Text style={s.actionBtnText}>
                {next === 'IN_PREPARATION' ? 'Preparar' : 'Listo'}
              </Text>
            </TouchableOpacity>
          )}
        </View>
      </View>
    );
  }, [tick]);

  const grouped = {
    PENDING: orders.filter(o => o.status === 'PENDING'),
    IN_PREPARATION: orders.filter(o => o.status === 'IN_PREPARATION'),
    READY: orders.filter(o => o.status === 'READY'),
  };

  const renderCategory = useCallback(({ item: cat }) => (
    <View style={s.catSection}>
      <Text style={s.catTitle}>{cat.name}</Text>
      {(cat.products || []).map((p) => (
        <TouchableOpacity key={p.id} style={[s.prodCard, !p.active && s.prodCardInactive]} onPress={() => openProductDetail(p)}>
          <View style={s.prodRow}>
            <View style={{ flex: 1 }}>
              <Text style={[s.prodName, !p.active && s.prodNameInactive]} numberOfLines={2}>{p.name}</Text>
              <Text style={s.prodPrice}>{p.price?.toLocaleString?.() || p.price}</Text>
            </View>
            <TouchableOpacity
              style={[s.availBadge, p.active ? s.availBadgeOn : s.availBadgeOff]}
              onPress={() => handleToggleProductAvailability(p)}>
              <Text style={[s.availBadgeText, p.active ? s.availBadgeTextOn : s.availBadgeTextOff]}>
                {p.active ? 'Disponible' : 'No disp.'}
              </Text>
            </TouchableOpacity>
          </View>
        </TouchableOpacity>
      ))}
    </View>
  ), [handleToggleProductAvailability]);

  const renderInventory = useCallback(({ item }) => {
    const isLow = item.quantityAvailable <= item.minThreshold;
    const isAvailable = item.quantityAvailable > 0;
    return (
      <View style={[s.invCard, isLow && s.invCardLow]}>
        <View style={s.invRow}>
          <View style={{ flex: 1 }}>
            <Text style={s.invName}>{item.ingredientName}</Text>
            <Text style={s.invDetail}>{item.quantityAvailable?.toFixed?.(1) || 0} {item.unit} (mín: {item.minThreshold})</Text>
            {isLow && <Text style={s.invAlert}>Stock bajo</Text>}
          </View>
          <View style={s.invActions}>
            {isAvailable ? (
              <TouchableOpacity
                style={[s.invBtn, s.invBtnRed]}
                onPress={() => handleToggleIngredientStock(item.ingredientId, item.ingredientName, true)}>
                <Text style={s.invBtnText}>Reportar sin stock</Text>
              </TouchableOpacity>
            ) : (
              <Text style={s.invAdminOnly}>Admin repone</Text>
            )}
          </View>
        </View>
      </View>
    );
  }, [handleToggleIngredientStock]);

  return (
    <View style={s.container}>
      <ConnectionIndicator connected={connected} style={s.connection} />
      {/* Tabs */}
      <View style={s.tabs}>
        <TouchableOpacity style={[s.tab, tab === 'orders' && s.tabActive]} onPress={() => setTab('orders')}>
          <Ionicons name="restaurant" size={18} color={tab === 'orders' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'orders' && s.tabTextActive]} numberOfLines={1}>Pedidos</Text>
          <Text style={[s.tabCount, tab === 'orders' && s.tabCountActive]}>{orders.length}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.tab, tab === 'menu' && s.tabActive]} onPress={() => setTab('menu')}>
          <Ionicons name="book-outline" size={18} color={tab === 'menu' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'menu' && s.tabTextActive]} numberOfLines={1}>Menú</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.tab, tab === 'inventory' && s.tabActive]} onPress={() => setTab('inventory')}>
          <Ionicons name="cube-outline" size={18} color={tab === 'inventory' ? '#f59e0b' : '#94a3b8'} />
          <Text style={[s.tabText, tab === 'inventory' && s.tabTextActive]} numberOfLines={1}>Stock</Text>
        </TouchableOpacity>
      </View>

      {tab === 'orders' && (
        <View style={s.container}>
          <View style={s.statsBar}>
            {Object.entries(grouped).map(([status, list]) => (
              <View key={status} style={s.statItem}>
                <Text style={[s.statNum, { color: STATUS_COLORS[status] }]}>{list.length}</Text>
                <Text style={s.statLabel} numberOfLines={2}>{SHORT_LABELS[status] || status}</Text>
              </View>
            ))}
          </View>
          <FlatList data={orders} renderItem={renderOrder} keyExtractor={(item) => String(item.orderId)}
            contentContainerStyle={s.list}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}
            ListEmptyComponent={
              <View style={s.empty}>
                <Ionicons name="restaurant-outline" size={48} color="#334155" />
                <Text style={s.emptyText}>No hay pedidos activos</Text>
              </View>
            }
          />
        </View>
      )}

      {tab === 'menu' && (
        <FlatList data={menuData} renderItem={renderCategory} keyExtractor={(item) => String(item.id)}
          contentContainerStyle={s.list}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}
          ListEmptyComponent={
            <View style={s.empty}>
              <Ionicons name="book-outline" size={48} color="#334155" />
              <Text style={s.emptyText}>Menú vacío</Text>
            </View>
          }
        />
      )}

      {tab === 'inventory' && (
        <FlatList data={inventory} renderItem={renderInventory} keyExtractor={(item) => String(item.ingredientId)}
          contentContainerStyle={s.list}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}
          ListEmptyComponent={
            <View style={s.empty}>
              <Ionicons name="cube-outline" size={48} color="#334155" />
              <Text style={s.emptyText}>Inventario vacío</Text>
            </View>
          }
        />
      )}

      <Modal visible={productModalVisible} transparent animationType="slide" onRequestClose={() => setProductModalVisible(false)}>
        <View style={s.modalOverlay}>
          <View style={s.modalContent}>
            <View style={s.modalHeader}>
              <Text style={s.modalTitle}>{selectedProduct?.name}</Text>
              <TouchableOpacity onPress={() => setProductModalVisible(false)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                <Ionicons name="close" size={24} color="#94a3b8" />
              </TouchableOpacity>
            </View>
            <ScrollView>
              <Text style={s.modalDesc}>{selectedProduct?.description}</Text>
              <Text style={s.modalPrice}>${selectedProduct?.price?.toLocaleString?.() || selectedProduct?.price}</Text>
              <View style={s.modalBadgeRow}>
                <View style={[s.badge, selectedProduct?.active ? s.badgeActive : s.badgeInactive]}>
                  <Text style={s.badgeText}>{selectedProduct?.active ? 'Disponible' : 'No disponible'}</Text>
                </View>
              </View>
              <Text style={s.sectionTitle}>Ingredientes</Text>
              {selectedProduct?.ingredients?.map((ing) => (
                <View key={ing.id} style={[s.ingRow, !ing.isAvailable && s.ingRowUnavailable, ing.isLow && s.ingRowLow]}>
                  <View style={{ flex: 1 }}>
                    <Text style={s.ingName}>{ing.name}</Text>
                    <Text style={s.ingType}>{ing.type} · {ing.quantityAvailable?.toFixed?.(1) || ing.quantityAvailable} {ing.unit}</Text>
                  </View>
                  <View style={s.ingStatusCol}>
                    {ing.isAvailable ? (
                      <>
                        <Text style={s.ingStatusOk}>OK</Text>
                        <TouchableOpacity style={s.ingDepleteBtn} onPress={() => { setProductModalVisible(false); handleToggleIngredientStock(ing.id, ing.name, true); }}>
                          <Text style={s.ingDepleteText}>Sin stock</Text>
                        </TouchableOpacity>
                      </>
                    ) : <Text style={s.invAdminOnly}>Admin repone</Text>}
                    {ing.isLow && <Text style={s.ingStatusLow}>⚠ Bajo</Text>}
                  </View>
                </View>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f1a' },
  connection: { alignSelf: 'flex-end', marginTop: 8, marginRight: 16 },
  statsBar: { flexDirection: 'row', justifyContent: 'space-around', paddingVertical: 16, paddingHorizontal: 8, borderBottomWidth: 1, borderBottomColor: '#1e1e35' },
  statItem: { alignItems: 'center', flex: 1, paddingHorizontal: 4 },
  statNum: { fontSize: 28, fontWeight: '800' },
  statLabel: { fontSize: 12, color: '#94a3b8', marginTop: 4, textAlign: 'center' },
  list: { padding: 16, gap: 12, paddingBottom: 24 },
  card: { backgroundColor: '#1a1a2e', borderRadius: 16, padding: 16, borderLeftWidth: 4 },
  cardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12, gap: 8 },
  tableLabel: { fontSize: 20, fontWeight: '800', color: '#f59e0b' },
  orderId: { fontSize: 13, color: '#94a3b8', marginTop: 2 },
  statusBadge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6, alignItems: 'center', justifyContent: 'center', flexShrink: 1 },
  statusText: { fontSize: 12, fontWeight: '700', textAlign: 'center' },
  itemsList: { gap: 6, marginBottom: 12 },
  itemRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  itemQty: { fontSize: 16, fontWeight: '700', color: '#f59e0b', width: 32, marginTop: 1 },
  itemName: { fontSize: 15, color: '#e2e8f0', flex: 1 },
  itemMods: { fontSize: 12, color: '#94a3b8', fontStyle: 'italic' },
  cardFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', borderTopWidth: 1, borderTopColor: '#2d2d44', paddingTop: 12, gap: 10, flexWrap: 'wrap' },
  timeWrap: { flexDirection: 'row', alignItems: 'center', gap: 6, flexShrink: 1 },
  timeLabel: { fontSize: 14, color: '#94a3b8' },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: 12, paddingHorizontal: 16, paddingVertical: 10, flexShrink: 0 },
  actionBtnText: { fontSize: 15, fontWeight: '700', color: '#fff' },
  empty: { alignItems: 'center', justifyContent: 'center', paddingVertical: 80, gap: 12 },
  emptyText: { fontSize: 16, color: '#94a3b8', textAlign: 'center' },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: '#1e1e35', backgroundColor: '#12121f' },
  tab: { flex: 1, paddingVertical: 10, alignItems: 'center', gap: 2 },
  tabActive: { borderBottomWidth: 3, borderBottomColor: '#f59e0b' },
  tabText: { fontSize: 12, color: '#94a3b8', fontWeight: '700' },
  tabTextActive: { color: '#f59e0b' },
  tabCount: { fontSize: 11, color: '#64748b', fontWeight: '700' },
  tabCountActive: { color: '#f59e0b' },
  invCard: { backgroundColor: '#1a1a2e', borderRadius: 14, padding: 14, borderLeftWidth: 4, borderLeftColor: '#22c55e' },
  invCardLow: { borderLeftColor: '#ef4444' },
  invRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 },
  invName: { fontSize: 16, fontWeight: '600', color: '#e2e8f0' },
  invDetail: { fontSize: 13, color: '#94a3b8', marginTop: 2 },
  invActions: { flexShrink: 0, maxWidth: '42%' },
  invBtn: { borderRadius: 10, paddingHorizontal: 10, paddingVertical: 8 },
  invBtnGreen: { backgroundColor: '#22c55e' },
  invBtnRed: { backgroundColor: '#ef4444' },
  invBtnText: { color: '#fff', fontSize: 12, fontWeight: '700', textAlign: 'center' },
  invAdminOnly: { color: '#94a3b8', fontSize: 12, fontWeight: '700', textAlign: 'right' },
  invAlert: { fontSize: 12, color: '#ef4444', marginTop: 4, fontWeight: '700' },
  // Menu
  catSection: { marginBottom: 20 },
  catTitle: { fontSize: 18, fontWeight: '800', color: '#f59e0b', marginBottom: 10, textAlign: 'center' },
  prodCard: { backgroundColor: '#1a1a2e', borderRadius: 12, padding: 14, marginBottom: 8, borderLeftWidth: 4, borderLeftColor: '#22c55e' },
  prodCardInactive: { borderLeftColor: '#ef4444', opacity: 0.7 },
  prodRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10 },
  prodName: { fontSize: 16, fontWeight: '600', color: '#e2e8f0' },
  prodNameInactive: { color: '#64748b' },
  prodPrice: { fontSize: 13, color: '#94a3b8', marginTop: 2 },
  availBadge: { borderRadius: 8, paddingHorizontal: 12, paddingVertical: 6, borderWidth: 1, flexShrink: 0 },
  availBadgeOn: { backgroundColor: '#22c55e22', borderColor: '#22c55e' },
  availBadgeOff: { backgroundColor: '#ef444422', borderColor: '#ef4444' },
  availBadgeText: { fontSize: 12, fontWeight: '700' },
  availBadgeTextOn: { color: '#22c55e' },
  availBadgeTextOff: { color: '#ef4444' },
  // Modal
  modalOverlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, maxHeight: '85%' },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  modalTitle: { fontSize: 22, fontWeight: '800', color: '#f59e0b', flex: 1 },
  modalClose: { fontSize: 24, color: '#64748b', padding: 4 },
  badge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4 },
  badgeActive: { backgroundColor: '#22c55e33' },
  badgeInactive: { backgroundColor: '#ef444433' },
  badgeText: { fontSize: 12, fontWeight: '700', color: '#e2e8f0' },
  modalDesc: { fontSize: 14, color: '#94a3b8', marginBottom: 8 },
  modalPrice: { fontSize: 20, fontWeight: '700', color: '#e2e8f0', marginBottom: 12 },
  modalBadgeRow: { flexDirection: 'row', marginBottom: 16 },
  sectionTitle: { fontSize: 16, fontWeight: '700', color: '#e2e8f0', marginBottom: 10, marginTop: 4 },
  ingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#2d2d44' },
  ingRowUnavailable: { backgroundColor: '#2d151522' },
  ingRowLow: { backgroundColor: '#3d2e1a22' },
  ingName: { fontSize: 15, color: '#e2e8f0', fontWeight: '600' },
  ingType: { fontSize: 12, color: '#94a3b8', marginTop: 2 },
  ingStatusCol: { alignItems: 'flex-end' },
  ingStatusOk: { fontSize: 13, color: '#22c55e', fontWeight: '700' },
  ingStatusNo: { fontSize: 13, color: '#ef4444', fontWeight: '700' },
  ingStatusLow: { fontSize: 12, color: '#f59e0b', marginTop: 2 },
  ingDepleteBtn: { marginTop: 6, backgroundColor: '#ef4444', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4 },
  ingDepleteText: { color: '#fff', fontSize: 11, fontWeight: '700' },
});
