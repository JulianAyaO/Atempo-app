import React, { useState, useEffect, useCallback } from 'react';
import {
  View, Text, ScrollView, TouchableOpacity, StyleSheet, RefreshControl,
  Modal, TextInput, Alert, Switch, KeyboardAvoidingView, Platform
} from 'react-native';
import { File, Paths } from 'expo-file-system';
import * as FileSystemLegacy from 'expo-file-system/legacy';
import * as Sharing from 'expo-sharing';
import { Ionicons } from '@expo/vector-icons';
import { useAuthContext } from '../context/AuthContext';
import {
  getSalesReport, getDashboard, getLiveDashboard, getActiveTables, getPeriodExportUrl
} from '../api/reportApi';
import {
  getInventory, getInventoryAlerts, getMenuWithStock,
  restockIngredient, setIngredientStock, markIngredientUnavailable
} from '../api/inventoryApi';
import {
  getCategories, createCategory,
  getIngredients, createIngredient, updateIngredient, deleteIngredient,
  createProduct, updateProduct, deleteProduct
} from '../api/catalogApi';
import { useWebSocket } from '../hooks/useWebSocket';
import { formatCOP } from '../utils/currency';
import StatusBadge from '../components/StatusBadge';
import ConnectionIndicator from '../components/ConnectionIndicator';
import LoadingSpinner from '../components/LoadingSpinner';
import { STATUS_COLORS, STATUS_LABELS } from '../constants/orderStatus';

export default function AdminScreen() {
  const { token } = useAuthContext();
  const { subscribe, connected } = useWebSocket();
  const [tab, setTab] = useState('dashboard');
  const [refreshing, setRefreshing] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  // Dashboard states
  const [liveDash, setLiveDash] = useState(null);
  const [dashToday, setDashToday] = useState(null);
  const [sales, setSales] = useState(null);
  const [activeTables, setActiveTables] = useState([]);
  const [expandedTableId, setExpandedTableId] = useState(null);

  // Reports states
  const [period, setPeriod] = useState('today');
  const [exporting, setExporting] = useState(null);

  // Inventory states
  const [inventory, setInventory] = useState([]);
  const [lowStock, setLowStock] = useState([]);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [editIngredient, setEditIngredient] = useState(null);
  const [editQty, setEditQty] = useState('');

  // Menu states
  const [menuData, setMenuData] = useState([]);
  const [categories, setCategories] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [productModalVisible, setProductModalVisible] = useState(false);
  const [productFormVisible, setProductFormVisible] = useState(false);
  const [ingredientFormVisible, setIngredientFormVisible] = useState(false);
  const [editProduct, setEditProduct] = useState(null);
  const [editIng, setEditIng] = useState(null);

  // Form states
  const [prodName, setProdName] = useState('');
  const [prodDesc, setProdDesc] = useState('');
  const [prodPrice, setProdPrice] = useState('');
  const [prodCategoryId, setProdCategoryId] = useState('');
  const [newCategoryName, setNewCategoryName] = useState('');
  const [prodIngredients, setProdIngredients] = useState([]);
  const [ingName, setIngName] = useState('');
  const [ingUnit, setIngUnit] = useState('gramos');

  const loadAll = useCallback(async ({ silent } = {}) => {
    if (!silent) setIsLoading(true);
    const fetchSafe = async (label, fn) => {
      try {
        const data = await fn();
        if (!silent) console.log(`[Admin] ${label}:`, data);
        return data;
      } catch (e) {
        console.warn(`[Admin] ${label} error:`, e.message);
        return null;
      }
    };
    const liveData = await fetchSafe('liveDash', () => getLiveDashboard(token));
    const dashData = await fetchSafe('dash', () => getDashboard(null, token));
    const salesData = await fetchSafe('sales', () => getSalesReport(period, token));
    const weekSales = period === 'today'
      ? await fetchSafe('salesWeek', () => getSalesReport('week', token))
      : null;
    const tablesData = await fetchSafe('tables', () => getActiveTables(token));
    const invData = await fetchSafe('inventory', () => getInventory(token));
    const alertsData = await fetchSafe('alerts', () => getInventoryAlerts(token));
    const menuData = await fetchSafe('menu', () => getMenuWithStock(token));
    const ingData = await fetchSafe('ingredients', () => getIngredients(token));
    const catData = await fetchSafe('categories', () => getCategories(token));

    if (liveData) setLiveDash(liveData);
    if (dashData) setDashToday(dashData);
    if (salesData) {
      setSales({
        ...salesData,
        byDay: (weekSales?.byDay?.length ? weekSales.byDay : salesData.byDay),
      });
    }
    if (tablesData !== null) {
      const sorted = [...tablesData].sort((a, b) => {
        const na = Number(a.tableNumber ?? a.tableId);
        const nb = Number(b.tableNumber ?? b.tableId);
        return (isNaN(na) ? 0 : na) - (isNaN(nb) ? 0 : nb);
      });
      setActiveTables(sorted);
    }
    if (invData) setInventory(invData);
    if (alertsData) setLowStock(alertsData);
    if (menuData) setMenuData(menuData);
    if (ingData) setIngredients(ingData);
    if (catData) setCategories(catData);
    setIsLoading(false);
    return [liveData, dashData, salesData, tablesData, invData, alertsData, menuData, ingData].some(v => v === null) ? false : true;
  }, [token, period]);

  useEffect(() => {
    loadAll();
    const sub1 = subscribe('/topic/admin/events', () => loadAll({ silent: true }));
    const sub2 = subscribe('/topic/menu/updates', () => loadAll({ silent: true }));
    const sub3 = subscribe('/topic/kitchen/inventory', () => loadAll({ silent: true }));
    const interval = setInterval(() => loadAll({ silent: true }), 10000);
    return () => { clearInterval(interval); if (sub1) sub1(); if (sub2) sub2(); if (sub3) sub3(); };
  }, [loadAll, subscribe]);

  const onRefresh = async () => {
    setRefreshing(true);
    const ok = await loadAll({ silent: true });
    setRefreshing(false);
    if (!ok) Alert.alert('Conexión', 'Algunas secciones no pudieron actualizarse.');
  };

  // ─── INVENTORY ACTIONS ───
  const handleRestock = async (id, qty) => {
    try { await restockIngredient(id, qty, token); loadAll(); } catch (e) { Alert.alert('Error', e.message); }
  };
  const openEditModal = (item) => { setEditIngredient(item); setEditQty(String(item.quantityAvailable)); setEditModalVisible(true); };
  const handleSetStock = async () => {
    if (!editIngredient) return;
    try {
      const qty = parseFloat(editQty);
      if (isNaN(qty) || qty < 0) { Alert.alert('Error', 'Cantidad inválida'); return; }
      await setIngredientStock(editIngredient.ingredientId, qty, token);
      loadAll(); setEditModalVisible(false);
    } catch (e) { Alert.alert('Error', e.message); }
  };
  const handleMarkUnavailable = async (id, name) => {
    Alert.alert('Sin stock', `¿Marcar "${name}" sin stock?`,
      [{ text: 'Cancelar', style: 'cancel' }, { text: 'Confirmar', onPress: async () => { try { await markIngredientUnavailable(id, token); loadAll(); } catch (e) { Alert.alert('Error', e.message); } } }]
    );
  };

  // ─── REPORT ACTIONS ───
  const handleExport = async (type) => {
    setExporting(type);
    try {
      const url = getPeriodExportUrl(type, period);
      const filename = type === 'csv' ? `reporte_${period}.csv` : `reporte_${period}.pdf`;
      const res = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) throw new Error(`No se pudo generar el reporte (HTTP ${res.status})`);
      const bytes = new Uint8Array(await res.arrayBuffer());
      let uri = null;
      try {
        const file = new File(Paths.cache, filename);
        if (file.exists) file.delete();
        file.write(bytes);
        uri = file.uri;
      } catch (_) {
        const dest = `${FileSystemLegacy.cacheDirectory}${filename}`;
        let binary = '';
        const chunk = 0x8000;
        for (let i = 0; i < bytes.length; i += chunk) {
          binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
        }
        await FileSystemLegacy.writeAsStringAsync(dest, btoa(binary), {
          encoding: FileSystemLegacy.EncodingType.Base64,
        });
        uri = dest;
      }
      const canShare = await Sharing.isAvailableAsync();
      if (!canShare) throw new Error('Compartir no está disponible en este dispositivo');
      await Sharing.shareAsync(uri, {
        mimeType: type === 'csv' ? 'text/csv' : 'application/pdf',
        dialogTitle: 'Compartir reporte',
        UTI: type === 'csv' ? 'public.comma-separated-values-text' : 'com.adobe.pdf',
      });
    } catch (e) {
      Alert.alert('Error', e.message || 'No se pudo exportar el reporte');
    }
    setExporting(null);
  };

  // ─── PRODUCT CRUD ───
  const openProductForm = (product = null) => {
    setEditProduct(product);
    if (product) {
      setProdName(product.name);
      setProdDesc(product.description || '');
      setProdPrice(String(product.price));
      setProdCategoryId(product.categoryId ? String(product.categoryId) : '');
      setNewCategoryName('');
      setProdIngredients((product.ingredients || []).map(i => ({
        ingredientId: i.ingredientId || i.id,
        ingredientName: i.ingredientName || i.name || '',
        type: i.type || 'BASE',
        quantityRequired: String(i.quantityRequired || 1),
        extraPrice: String(i.extraPrice || 0),
      })));
    } else {
      setProdName(''); setProdDesc(''); setProdPrice(''); setProdIngredients([]);
      setProdCategoryId(menuData?.[0]?.id ? String(menuData[0].id) : '');
      setNewCategoryName('');
    }
    setProductFormVisible(true);
  };
  const handleSaveProduct = async () => {
    try {
      const price = parseFloat(prodPrice);
      if (!prodName || isNaN(price)) { Alert.alert('Error', 'Nombre y precio son obligatorios'); return; }
      if (!editProduct && prodIngredients.length === 0) { Alert.alert('Error', 'Debes agregar al menos un ingrediente'); return; }
      let categoryId = prodCategoryId ? Number(prodCategoryId) : null;
      if (newCategoryName.trim()) {
        const createdCategory = await createCategory({
          name: newCategoryName.trim(),
          description: '',
          displayOrder: categories.length + 1,
        }, token);
        categoryId = createdCategory.id;
      }
      if (!categoryId) { Alert.alert('Error', 'Selecciona una categoría o crea una nueva'); return; }

      // Resolve ingredient IDs (create if missing)
      const resolvedIngredients = [];
      const newlyCreatedIds = new Set();
      for (const pi of prodIngredients) {
        let ingId = pi.ingredientId;
        if (!ingId) {
          const existing = ingredients.find(i => i.name.trim().toLowerCase() === (pi.ingredientName || '').trim().toLowerCase());
          if (existing) {
            ingId = existing.id;
          } else {
            const created = await createIngredient({ name: pi.ingredientName, unit: 'unidad' }, token);
            ingId = created.id;
            newlyCreatedIds.add(ingId);
          }
        }
        resolvedIngredients.push({
          ingredientId: Number(ingId),
          type: pi.type || 'BASE',
          quantityRequired: Number(pi.quantityRequired) || 1,
          extraPrice: Number(pi.extraPrice) || 0,
        });
      }

      const data = { name: prodName, description: prodDesc, price, categoryId, ingredients: resolvedIngredients };

      if (editProduct) {
        await updateProduct(editProduct.id, data, token);
        Alert.alert('Actualizado', 'Producto modificado');
      } else {
        await createProduct(data, token);
        Alert.alert('Creado', 'Producto agregado al menú');
        // Restock newly created ingredients with default quantity
        for (const ri of resolvedIngredients) {
          if (newlyCreatedIds.has(ri.ingredientId)) {
            try { await restockIngredient(ri.ingredientId, 100, token); } catch (_) { /* ignore */ }
          }
        }
      }
      setProductFormVisible(false); loadAll();
    } catch (e) { Alert.alert('Error', e.message); }
  };
  const handleToggleProduct = async (product) => {
    try {
      await updateProduct(product.id, { active: !product.active }, token);
      loadAll();
    } catch (e) { Alert.alert('Error', e.message); }
  };
  const handleDeleteProduct = async (id) => {
    Alert.alert('Confirmar', '¿Desactivar este producto?', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Desactivar', style: 'destructive', onPress: async () => { try { await deleteProduct(id, token); loadAll(); } catch (e) { Alert.alert('Error', e.message); } } }
    ]);
  };

  // ─── INGREDIENT CRUD ───
  const openIngredientForm = (ing = null) => {
    setEditIng(ing);
    if (ing) { setIngName(ing.name); setIngUnit(ing.unit || 'gramos'); }
    else { setIngName(''); setIngUnit('gramos'); }
    setIngredientFormVisible(true);
  };
  const handleSaveIngredient = async () => {
    try {
      if (!ingName) { Alert.alert('Error', 'Nombre del ingrediente obligatorio'); return; }
      const data = { name: ingName, unit: ingUnit };
      if (editIng) { await updateIngredient(editIng.id, data, token); Alert.alert('Actualizado', 'Ingrediente modificado'); }
      else { await createIngredient(data, token); Alert.alert('Creado', 'Ingrediente creado'); }
      setIngredientFormVisible(false); loadAll();
    } catch (e) { Alert.alert('Error', e.message); }
  };
  const handleDeleteIngredient = async (id) => {
    Alert.alert('Confirmar', '¿Desactivar este ingrediente?', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Desactivar', style: 'destructive', onPress: async () => { try { await deleteIngredient(id, token); loadAll(); } catch (e) { Alert.alert('Error', e.message); } } }
    ]);
  };

  // ─── RENDER HELPERS ───
  const MetricCard = ({ icon, value, label, color }) => (
    <View style={[s.metricCard, { borderLeftColor: color }]}>
      <Ionicons name={icon} size={24} color={color} />
      <Text style={s.metricValue}>{value}</Text>
      <Text style={s.metricLabel}>{label}</Text>
    </View>
  );

  const getTableFlowStatus = (orders, occupied) => {
    if (!occupied) return { status: 'FREE', label: STATUS_LABELS.FREE };
    if (!orders || orders.length === 0) return { status: 'OCCUPIED_EMPTY', label: STATUS_LABELS.OCCUPIED_EMPTY };
    const flow = ['DRAFT', 'PENDING', 'IN_PREPARATION', 'READY', 'DELIVERED', 'PAYMENT_REQUESTED', 'PAID'];
    const statuses = orders.map(o => o.status);
    for (let i = flow.length - 1; i >= 0; i--) {
      if (statuses.includes(flow[i])) return { status: flow[i], label: STATUS_LABELS[flow[i]] };
    }
    return { status: orders[0].status, label: STATUS_LABELS[orders[0].status] || orders[0].status };
  };

  const CAT_COLORS = ['#f59e0b', '#3b82f6', '#22c55e', '#a855f7', '#ef4444', '#06b6d4', '#eab308', '#f97316'];

  const formatChartMoney = (amount) => {
    const v = Number(amount) || 0;
    if (v >= 1000000) return `$${(v / 1000000).toFixed(1)}M`;
    if (v >= 1000) return `$${Math.round(v / 1000)}k`;
    return `$${Math.round(v)}`;
  };

  const rowsWithSales = (rows, limit) => {
    const filled = (rows || [])
      .map((r) => ({
        ...r,
        sales: Number(r.sales || 0),
        orders: Number(r.orders || 0),
      }))
      .filter((r) => r.sales > 0);
    return filled.length > limit ? filled.slice(filled.length - limit) : filled;
  };

  const lastThreeDays = (rows) => {
    const map = {};
    (rows || []).forEach((r) => {
      map[String(r.day || '').slice(0, 10)] = {
        sales: Number(r.sales || 0),
        orders: Number(r.orders || 0),
      };
    });
    const labels = ['Antier', 'Ayer', 'Hoy'];
    const out = [];
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    for (let i = 2; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(today.getDate() - i);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      const hit = map[key] || { sales: 0, orders: 0 };
      out.push({
        day: key,
        sales: hit.sales,
        orders: hit.orders,
        label: labels[2 - i],
      });
    }
    return out;
  };

  const BarRow = ({ label, value, max, color, right }) => {
    const pct = max > 0 ? Math.min(100, (Number(value) / max) * 100) : 0;
    return (
      <View style={s.chartRow}>
        <Text style={s.chartLabel} numberOfLines={1}>{label}</Text>
        <View style={s.chartTrack}>
          <View style={[s.chartFill, { width: `${Math.max(pct, 2)}%`, backgroundColor: color }]} />
        </View>
        <Text style={s.chartValue}>{right}</Text>
      </View>
    );
  };

  // ─── DASHBOARD TAB ───
  const renderDashboardTab = () => {
    const occupiedCount = activeTables.filter(t => t.occupied).length;
    return (
    <ScrollView style={s.tabContent} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}>
      {isLoading ? (
        <View style={{ paddingVertical: 40, alignItems: 'center' }}>
          <LoadingSpinner />
          <Text style={{ color: '#94a3b8', marginTop: 12, fontSize: 16 }}>Cargando panel...</Text>
        </View>
      ) : (
        <View>
          <Text style={s.sectionTitle}>Mesas</Text>
          <Text style={s.floorSummary}>{occupiedCount} ocupadas · {activeTables.length - occupiedCount} libres · {activeTables.length} en total</Text>
          {activeTables.length > 0 ? (
            <View>
              {activeTables.map((table) => {
                const occupied = !!table.occupied;
                const flow = getTableFlowStatus(table.orders, occupied);
                const display = table.tableNumber ?? table.tableId;
                const expanded = expandedTableId === table.tableId;
                const color = occupied ? (STATUS_COLORS[flow.status] || '#f59e0b') : STATUS_COLORS.FREE;
                const cardTone = !occupied ? s.tableCardFree
                  : flow.status === 'OCCUPIED_EMPTY' ? s.tableCardEmpty
                  : flow.status === 'DRAFT' ? s.tableCardDraft
                  : s.tableCardBusy;
                return (
                  <TouchableOpacity
                    key={table.tableId}
                    style={[s.tableCard, cardTone]}
                    activeOpacity={0.85}
                    onPress={() => setExpandedTableId(expanded ? null : table.tableId)}
                  >
                    <View style={s.tableCardHeader}>
                      <View>
                        <Text style={s.tableCardTitle}>{table.name || `Mesa ${display}`}</Text>
                        <Text style={s.tableMeta}>
                          {occupied ? 'Ocupada' : 'Libre'}
                          {table.capacity ? ` · ${table.capacity} pers.` : ''}
                          {occupied ? ` · ${formatCOP(table.generated)}` : ''}
                        </Text>
                      </View>
                      <View style={[s.flowBadge, { backgroundColor: color + '33', borderColor: color }]}>
                        <Text style={[s.flowBadgeText, { color }]}>{flow.label}</Text>
                      </View>
                    </View>
                    {expanded && occupied && (
                      <View>
                        {table.sessionStartedAt ? (
                          <Text style={s.tableMeta}>Sesión desde {String(table.sessionStartedAt).replace('T', ' ').substring(0, 16)}</Text>
                        ) : null}
                        {table.orders?.length > 0 ? (
                          table.orders.map((o) => (
                            <View key={o.orderId} style={s.tableOrderBlock}>
                              <View style={s.tableOrderRow}>
                                <StatusBadge status={o.status} />
                                <Text style={s.tableOrderInfo}>Pedido #{o.orderId}</Text>
                                <Text style={s.tableOrderTotal}>{formatCOP(o.total)}</Text>
                              </View>
                              {(o.items || []).length === 0 ? (
                                <Text style={s.tableEmptyLine}>Sin platillos aún</Text>
                              ) : (
                                o.items.map((it) => (
                                  <View key={it.itemId} style={s.tableItemRow}>
                                    <Text style={s.tableItemQty}>{it.quantity}×</Text>
                                    <Text style={s.tableItemName}>{it.productName}</Text>
                                    <Text style={s.tableItemPrice}>{formatCOP(it.lineTotal)}</Text>
                                  </View>
                                ))
                              )}
                              {o.notes ? <Text style={s.tableEmptyLine}>{o.notes}</Text> : null}
                            </View>
                          ))
                        ) : (
                          <Text style={s.tableEmptyLine}>Mesa ocupada, todavía no confirman platillos.</Text>
                        )}
                      </View>
                    )}
                    {expanded && !occupied && (
                      <Text style={s.tableEmptyLine}>Esta mesa está libre. Toca otra mesa ocupada para ver el pedido.</Text>
                    )}
                    {!expanded && occupied && (
                      <Text style={s.tableHint}>Toca para ver el pedido y el proceso</Text>
                    )}
                  </TouchableOpacity>
                );
              })}
            </View>
          ) : (
            <View style={{ paddingVertical: 24, alignItems: 'center' }}>
              <Ionicons name="restaurant-outline" size={40} color="#334155" />
              <Text style={{ color: '#64748b', textAlign: 'center', marginTop: 12, fontSize: 16 }}>No hay mesas registradas</Text>
            </View>
          )}
          {liveDash && (
            <View>
              <Text style={s.sectionTitle}>Estado operativo</Text>
              <View style={s.metricsGrid}>
                <MetricCard icon="people" value={liveDash.activeTables} label="Mesas activas" color="#3b82f6" />
                <MetricCard icon="receipt" value={liveDash.pendingOrders} label="Pendientes" color="#f59e0b" />
                <MetricCard icon="time" value={liveDash.inPreparationOrders} label="En prep." color="#3b82f6" />
                <MetricCard icon="checkmark-done" value={liveDash.readyOrders} label="Listos" color="#22c55e" />
                <MetricCard icon="bicycle" value={liveDash.deliveredOrders} label="Entregados" color="#8b5cf6" />
                <MetricCard icon="document-text" value={liveDash.draftOrders} label="Borradores" color="#fb923c" />
              </View>

              <Text style={s.sectionTitle}>Finanzas hoy</Text>
              <View style={s.metricsGrid}>
                <MetricCard icon="cash" value={formatCOP(liveDash.todaySales)} label="Ventas" color="#22c55e" />
                <MetricCard icon="cart" value={liveDash.totalOrdersToday} label="Pedidos hoy" color="#f59e0b" />
                <MetricCard icon="checkmark-circle" value={liveDash.paidOrders + liveDash.closedOrders} label="Pagados" color="#10b981" />
                <MetricCard icon="close-circle" value={liveDash.cancelledOrders} label="Cancelados" color="#ef4444" />
              </View>

              {liveDash.topProductsToday?.length > 0 && (
                <View>
                  <Text style={s.sectionTitle}>Más vendidos hoy</Text>
                  {liveDash.topProductsToday.map((p, i) => (
                    <View key={i} style={s.topProdRow}>
                      <Text style={s.topProdRank}>{i + 1}</Text>
                      <View style={{ flex: 1 }}>
                        <Text style={s.topProdName}>{p.productName}</Text>
                        <Text style={s.topProdStats}>{p.totalQuantity} vendidos · {formatCOP(p.totalRevenue)}</Text>
                      </View>
                    </View>
                  ))}
                </View>
              )}

              {liveDash.recentActivity?.length > 0 && (
                <View>
                  <Text style={s.sectionTitle}>Actividad reciente</Text>
                  {liveDash.recentActivity.map((act, i) => (
                    <View key={i} style={s.activityRow}>
                      <StatusBadge status={act.status} />
                      <View style={{ flex: 1, marginLeft: 10 }}>
                        <Text style={s.activityText}>Mesa {act.tableId} — Pedido #{act.orderId}</Text>
                        <Text style={s.activityTime}>{act.timestamp?.replace('T', ' ')?.substring(0, 16)}</Text>
                      </View>
                      <Text style={s.activityTotal}>{formatCOP(act.total)}</Text>
                    </View>
                  ))}
                </View>
              )}
            </View>
          )}
        </View>
      )}
    </ScrollView>
    );
  };

  // ─── REPORTS TAB ───
  const renderReportsTab = () => {
    const byHour = rowsWithSales(sales?.byHour, 12);
    const byDay = lastThreeDays(sales?.byDay);
    const byCategory = sales?.byCategory || [];
    const topProducts = sales?.topProducts || [];
    const byStatus = sales?.byStatus || dashToday?.distribucionPorEstado || {};
    const maxDay = Math.max(0, ...byDay.map(d => Number(d.sales || 0)));
    const maxCat = Math.max(0, ...byCategory.map(c => Number(c.revenue || 0)));
    const maxTop = Math.max(0, ...topProducts.map(p => Number(p.totalRevenue || 0)));
    const maxStatus = Math.max(0, ...Object.values(byStatus).map(v => Number(v)));
    const maxHour = Math.max(0, ...byHour.map(h => Number(h.sales || 0)));
    const catTotal = byCategory.reduce((n, c) => n + Number(c.revenue || 0), 0);
    const periodLabel = period === 'today' ? 'hoy' : period === 'week' ? 'esta semana' : 'este mes';
    const prevLabel = period === 'today' ? 'ayer' : period === 'week' ? 'semana previa' : 'mes previo';
    const changePct = Number(sales?.salesChangePct || 0);
    const changeUp = changePct >= 0;

    return (
    <ScrollView style={s.tabContent} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}>
      <Text style={s.sectionTitle}>Reportes financieros</Text>

      <View style={s.periodRow}>
        {['today', 'week', 'month'].map(p => (
          <TouchableOpacity key={p} style={[s.periodBtn, period === p && s.periodActive]} onPress={() => setPeriod(p)}>
            <Text style={[s.periodText, period === p && s.periodTextActive]}>
              {p === 'today' ? 'Hoy' : p === 'week' ? 'Semana' : 'Mes'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      <View style={s.summaryGrid}>
        <View style={[s.summaryCard, { backgroundColor: '#1e3a5f' }]}>
          <Ionicons name="cash" size={26} color="#3b82f6" />
          <Text style={s.summaryValue}>{formatCOP(sales?.totalSales)}</Text>
          <Text style={s.summaryLabel}>Ventas {periodLabel}</Text>
          <Text style={[s.changeText, { color: changeUp ? '#22c55e' : '#ef4444' }]}>
            {changeUp ? '▲' : '▼'} {Math.abs(changePct).toFixed(1)}% vs {prevLabel}
          </Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: '#1a3d2e' }]}>
          <Ionicons name="receipt" size={26} color="#22c55e" />
          <Text style={s.summaryValue}>{sales?.totalOrders || 0}</Text>
          <Text style={s.summaryLabel}>Pedidos cobrados</Text>
          <Text style={s.changeMuted}>{formatCOP(sales?.previousPeriodSales)} {prevLabel}</Text>
        </View>
      </View>
      <View style={s.summaryGrid}>
        <View style={[s.summaryCard, { backgroundColor: '#3d2e1a' }]}>
          <Ionicons name="trending-up" size={26} color="#f59e0b" />
          <Text style={s.summaryValue}>{formatCOP(sales?.avgOrderValue)}</Text>
          <Text style={s.summaryLabel}>Ticket promedio</Text>
        </View>
        <View style={[s.summaryCard, { backgroundColor: '#3d1a1a' }]}>
          <Ionicons name="close-circle" size={26} color="#ef4444" />
          <Text style={s.summaryValue}>{sales?.cancelledOrders || 0}</Text>
          <Text style={s.summaryLabel}>Cancelados</Text>
        </View>
      </View>

      {byCategory.length > 0 && (
        <>
          <Text style={s.sectionTitle}>Mix de ventas por categoría</Text>
          <View style={s.mixBar}>
            {byCategory.map((c, i) => {
              const flex = Math.max(Number(c.revenue || 0), 1);
              return <View key={`${c.category}-${i}`} style={{ flex, backgroundColor: CAT_COLORS[i % CAT_COLORS.length] }} />;
            })}
          </View>
          {byCategory.map((c, i) => {
            const pct = catTotal > 0 ? (Number(c.revenue || 0) / catTotal) * 100 : 0;
            return (
              <View key={`leg-${c.category}-${i}`} style={s.legendRow}>
                <View style={[s.legendDot, { backgroundColor: CAT_COLORS[i % CAT_COLORS.length] }]} />
                <Text style={s.legendLabel}>{c.category}</Text>
                <Text style={s.legendPct}>{pct.toFixed(0)}%</Text>
                <Text style={s.legendVal}>{formatCOP(c.revenue)}</Text>
              </View>
            );
          })}
        </>
      )}

      {byHour.length > 0 ? (
        <>
          <Text style={s.sectionTitle}>Ventas por hora</Text>
          <View style={s.fullChart}>
            {byHour.map((h, i) => {
              const salesVal = Number(h.sales || 0);
              const height = maxHour > 0 ? Math.max(16, (salesVal / maxHour) * 100) : 16;
              return (
                <View key={`${h.hour}-${i}`} style={s.fullBarCol}>
                  <Text style={s.fullBarAmount} numberOfLines={1}>{formatChartMoney(salesVal)}</Text>
                  <Text style={s.fullBarOrders}>{h.orders} ped.</Text>
                  <View style={[s.fullBar, { height, backgroundColor: '#3b82f6' }]} />
                  <Text style={s.fullBarLabel}>{Number(h.hour)}h</Text>
                </View>
              );
            })}
          </View>
        </>
      ) : (
        <Text style={s.exportHint}>No hay ventas por hora en este periodo.</Text>
      )}

      {byDay.length > 0 ? (
        <>
          <Text style={s.sectionTitle}>Ventas por día</Text>
          <View style={s.fullChart}>
            {byDay.map((d, i) => {
              const salesVal = Number(d.sales || 0);
              const height = maxDay > 0 ? Math.max(16, (salesVal / maxDay) * 100) : 16;
              const label = d.label || (date.length >= 10 ? `${date.slice(8)}/${date.slice(5, 7)}` : date);
              return (
                <View key={`${d.day}-${i}`} style={s.fullBarCol}>
                  <Text style={s.fullBarAmount} numberOfLines={1}>{formatChartMoney(salesVal)}</Text>
                  <Text style={s.fullBarOrders}>{d.orders} ped.</Text>
                  <View style={[s.fullBar, { height }]} />
                  <Text style={s.fullBarLabel}>{label}</Text>
                </View>
              );
            })}
          </View>
        </>
      ) : (
        <Text style={s.exportHint}>No hay ventas por día en este periodo.</Text>
      )}

      {topProducts.length > 0 && (
        <>
          <Text style={s.sectionTitle}>Top productos por ingreso</Text>
          {topProducts.map((p, i) => (
            <BarRow
              key={p.productId || i}
              label={`${i + 1}. ${p.productName}`}
              value={p.totalRevenue}
              max={maxTop}
              color="#f59e0b"
              right={`${p.totalQuantity} · ${formatCOP(p.totalRevenue)}`}
            />
          ))}
        </>
      )}

      {Object.keys(byStatus).length > 0 && (
        <>
          <Text style={s.sectionTitle}>Pedidos por estado</Text>
          {Object.entries(byStatus).map(([status, count]) => (
            <View key={status} style={s.distRow}>
              <StatusBadge status={status} />
              <View style={{ flex: 1, height: 8, backgroundColor: '#1e1e35', borderRadius: 4, marginHorizontal: 10 }}>
                <View style={{ height: 8, backgroundColor: STATUS_COLORS[status] || '#64748b', borderRadius: 4, width: `${maxStatus ? Math.min(100, (Number(count) / maxStatus) * 100) : 0}%` }} />
              </View>
              <Text style={s.distCount}>{count}</Text>
            </View>
          ))}
        </>
      )}

      {!sales?.totalOrders && (
        <Text style={s.exportHint}>Aún no hay ventas cobradas en este periodo.</Text>
      )}

      <Text style={s.sectionTitle}>Exportar</Text>
      <Text style={s.exportHint}>El CSV y el PDF incluyen ventas, ticket, categorías, top productos, estados e inventario consumido del periodo seleccionado.</Text>
      <View style={s.exportRow}>
        <TouchableOpacity style={[s.exportBtn, { backgroundColor: '#1e3a5f' }]} onPress={() => handleExport('csv')} disabled={!!exporting}>
          <Ionicons name="document-text" size={18} color="#3b82f6" />
          <Text style={s.exportBtnText}>{exporting === 'csv' ? '...' : 'Exportar CSV'}</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[s.exportBtn, { backgroundColor: '#2d1515' }]} onPress={() => handleExport('pdf')} disabled={!!exporting}>
          <Ionicons name="document" size={18} color="#ef4444" />
          <Text style={s.exportBtnText}>{exporting === 'pdf' ? '...' : 'Exportar PDF'}</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
    );
  };

  // ─── MENU TAB (same as kitchen) ───
  const renderMenuTab = () => (
    <ScrollView style={s.tabContent} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}>
      <View style={s.actionRow}>
        <TouchableOpacity style={s.actionBtn} onPress={() => openProductForm()}>
          <Ionicons name="add-circle" size={18} color="#22c55e" />
          <Text style={s.actionBtnText}>Añadir Producto</Text>
        </TouchableOpacity>
      </View>
      {menuData.map((cat) => (
        <View key={cat.id} style={s.catSection}>
          <View style={s.catHeader}>
            <Text style={s.catTitle}>{cat.name}</Text>
          </View>
          {(cat.products || []).map((p) => (
            <View key={p.id} style={[s.prodCard, !p.active && s.prodCardInactive]}>
              <View style={s.prodRow}>
                <TouchableOpacity style={{ flex: 1 }} onPress={() => { setSelectedProduct(p); setProductModalVisible(true); }}>
                  <Text style={[s.prodName, !p.active && s.prodNameInactive]}>{p.name}</Text>
                  <Text style={s.prodPrice}>{formatCOP(p.price)}</Text>
                  {p.ingredients && p.ingredients.length > 0 && (
                    <Text style={s.invUnit}>
                      {p.ingredients.map(i => i.name).join(', ')}
                    </Text>
                  )}
                </TouchableOpacity>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
                  <Switch value={p.active} onValueChange={() => handleToggleProduct(p)} trackColor={{ false: '#ef4444', true: '#22c55e' }} />
                  <TouchableOpacity onPress={() => openProductForm(p)}><Ionicons name="create-outline" size={20} color="#3b82f6" /></TouchableOpacity>
                  <TouchableOpacity onPress={() => handleDeleteProduct(p.id)}><Ionicons name="trash-outline" size={20} color="#ef4444" /></TouchableOpacity>
                </View>
              </View>
            </View>
          ))}
        </View>
      ))}
    </ScrollView>
  );

  // ─── INVENTORY TAB (same as kitchen) ───
  const renderInventoryTab = () => (
    <ScrollView style={s.tabContent} refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#f59e0b" />}>
      <View style={s.sectionHeader}>
        <Text style={s.sectionTitle}>Inventario</Text>
        <TouchableOpacity style={s.addBtn} onPress={() => openIngredientForm()}>
          <Ionicons name="add" size={18} color="#0f0f1a" />
          <Text style={s.addBtnText}>Ingrediente</Text>
        </TouchableOpacity>
      </View>
      {inventory.map((item, i) => {
        const isLow = item.quantityAvailable <= item.minThreshold;
        const isAvailable = item.quantityAvailable > 0;
        return (
          <View key={i} style={[s.invCard, isLow && s.invCardLow]}>
            <View style={s.invRow}>
              <View style={{ flex: 1 }}>
                <Text style={s.invName}>{item.ingredientName}</Text>
                <Text style={s.invDetail}>{item.quantityAvailable?.toFixed?.(1) || 0} {item.unit} (mín: {item.minThreshold})</Text>
                {isLow && <Text style={s.invAlert}>⚠️ Stock bajo</Text>}
              </View>
              <View style={s.invActions}>
                <TouchableOpacity style={[s.invBtn, s.invBtnGreen]} onPress={() => handleRestock(item.ingredientId, 50)}><Text style={s.invBtnText}>+50</Text></TouchableOpacity>
                <TouchableOpacity style={[s.invBtn, isAvailable ? s.invBtnRed : s.invBtnGreen]} onPress={() => handleMarkUnavailable(item.ingredientId, item.ingredientName)}><Text style={s.invBtnText}>{isAvailable ? 'Sin stock' : 'Reponer'}</Text></TouchableOpacity>
                <TouchableOpacity style={[s.invBtn, s.invBtnBlue]} onPress={() => openEditModal(item)}><Text style={s.invBtnText}>Editar</Text></TouchableOpacity>
              </View>
            </View>
          </View>
        );
      })}
    </ScrollView>
  );

  // ─── MODALS ───
  const renderProductModal = () => (
    <Modal visible={productModalVisible} transparent animationType="slide" onRequestClose={() => setProductModalVisible(false)}>
      <View style={s.modalOverlay}>
        <View style={s.modalContent}>
          <View style={s.modalHeader}>
            <Text style={s.modalTitle}>{selectedProduct?.name}</Text>
            <TouchableOpacity onPress={() => setProductModalVisible(false)}><Text style={s.modalClose}>✕</Text></TouchableOpacity>
          </View>
          <ScrollView>
            <Text style={s.modalDesc}>{selectedProduct?.description}</Text>
            <Text style={s.modalPrice}>${selectedProduct?.price?.toLocaleString?.() || selectedProduct?.price}</Text>
            <View style={s.modalBadgeRow}>
              <View style={[s.badge, selectedProduct?.active ? s.badgeActive : s.badgeInactive]}>
                <Text style={[s.badgeText, { color: selectedProduct?.active ? '#22c55e' : '#ef4444' }]}>{selectedProduct?.active ? 'Disponible' : 'No disponible'}</Text>
              </View>
            </View>
            <Text style={s.sectionTitle}>🧂 Ingredientes</Text>
            {(!selectedProduct?.ingredients || selectedProduct.ingredients.length === 0) && (
              <Text style={{ color: '#64748b', fontStyle: 'italic', textAlign: 'center', paddingVertical: 12 }}>Sin ingredientes configurados.</Text>
            )}
            {selectedProduct?.ingredients?.map((ing) => (
              <View key={ing.id} style={[s.ingRow, !ing.isAvailable && s.ingRowUnavailable, ing.isLow && s.ingRowLow]}>
                <View style={{ flex: 1 }}>
                  <Text style={s.ingName}>{ing.name}</Text>
                  <Text style={s.ingType}>{ing.type} · {ing.quantityAvailable?.toFixed?.(1) || ing.quantityAvailable} {ing.unit}</Text>
                </View>
                <View style={s.ingStatusCol}>
                  {ing.isAvailable ? <Text style={s.ingStatusOk}>OK</Text> : <Text style={s.ingStatusUnavailable}>Sin stock</Text>}
                  {ing.isLow && <Text style={s.ingStatusLow}>⚠ Bajo</Text>}
                </View>
              </View>
            ))}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );

  const renderFormModal = (visible, setVisible, title, children) => (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={() => setVisible(false)}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
        <View style={s.modalOverlay}>
          <View style={s.modalContent}>
            <Text style={s.modalTitle}>{title}</Text>
            <ScrollView
              keyboardShouldPersistTaps="handled"
              nestedScrollEnabled
              showsVerticalScrollIndicator
              style={{ maxHeight: 520 }}
              contentContainerStyle={{ paddingBottom: 16 }}
            >
              {children}
            </ScrollView>
            <TouchableOpacity style={[s.modalBtn, { backgroundColor: '#334155' }]} onPress={() => setVisible(false)}><Text style={s.modalBtnText}>Cancelar</Text></TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );

  return (
    <View style={s.container}>
      <ConnectionIndicator connected={connected} style={s.connection} />
      <View style={s.tabs}>
        {['dashboard', 'reports', 'menu', 'inventory'].map(t => (
          <TouchableOpacity key={t} style={[s.tabBtn, tab === t && s.tabBtnActive]} onPress={() => setTab(t)}>
            <Text style={[s.tabBtnText, tab === t && s.tabBtnTextActive]}>
              {t === 'dashboard' ? 'Panel' : t === 'reports' ? 'Reportes' : t === 'menu' ? 'Menú' : 'Inventario'}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      {tab === 'dashboard' && renderDashboardTab()}
      {tab === 'reports' && renderReportsTab()}
      {tab === 'menu' && renderMenuTab()}
      {tab === 'inventory' && renderInventoryTab()}
      {renderProductModal()}
      {renderFormModal(productFormVisible, setProductFormVisible, editProduct ? 'Editar Producto' : 'Nuevo Producto', (
        <>
          <TextInput style={s.input} placeholder="Nombre del producto" placeholderTextColor="#64748b" value={prodName} onChangeText={setProdName} />
          <TextInput style={s.input} placeholder="Descripción" placeholderTextColor="#64748b" value={prodDesc} onChangeText={setProdDesc} multiline />
          <TextInput style={s.input} placeholder="Precio" placeholderTextColor="#64748b" value={prodPrice} onChangeText={setProdPrice} keyboardType="numeric" />

          <Text style={{ color: '#94a3b8', marginBottom: 8 }}>Categoría:</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.ingPickerScroll}>
            {(categories.length > 0 ? categories : menuData).map(cat => {
              const selected = String(prodCategoryId || '') === String(cat.id);
              return (
                <TouchableOpacity key={cat.id} style={[s.ingPickerChip, selected && s.ingPickerChipActive]} onPress={() => {
                  setProdCategoryId(String(cat.id));
                  setNewCategoryName('');
                }}>
                  <Text style={[s.ingPickerChipText, selected && s.ingPickerChipTextActive]}>{cat.name}</Text>
                </TouchableOpacity>
              );
            })}
          </ScrollView>
          <TextInput style={s.input} placeholder="O crea nueva categoría (ej: Tacos)" placeholderTextColor="#64748b" value={newCategoryName} onChangeText={text => {
            setNewCategoryName(text);
            if (text.trim()) setProdCategoryId('');
          }} />

          <Text style={{ color: '#94a3b8', marginBottom: 8 }}>Ingredientes (nombre y cantidad):</Text>
          {prodIngredients.map((pi, idx) => (
            <View key={idx} style={s.prodIngCard}>
              <View style={{ flexDirection: 'row', gap: 8, marginBottom: 8 }}>
                <TextInput style={[s.input, { flex: 2, marginBottom: 0 }]} placeholder="Nombre ingrediente" placeholderTextColor="#64748b" value={pi.ingredientName || ''} onChangeText={text => {
                  const next = [...prodIngredients];
                  next[idx] = { ...next[idx], ingredientName: text, ingredientId: '' };
                  setProdIngredients(next);
                }} />
                <TextInput style={[s.input, { flex: 1, marginBottom: 0 }]} placeholder="Cant." placeholderTextColor="#64748b" keyboardType="numeric" value={pi.quantityRequired} onChangeText={text => {
                  const next = [...prodIngredients];
                  next[idx] = { ...next[idx], quantityRequired: text };
                  setProdIngredients(next);
                }} />
                <TouchableOpacity style={[s.invBtn, s.invBtnRed, { alignSelf: 'center' }]} onPress={() => setProdIngredients(prev => prev.filter((_, i) => i !== idx))}>
                  <Text style={s.invBtnText}>X</Text>
                </TouchableOpacity>
              </View>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.ingPickerScroll}>
                {['BASE', 'REMOVABLE', 'OPTIONAL'].map(tp => {
                  const selected = (pi.type || 'BASE') === tp;
                  const label = tp === 'BASE' ? 'Base' : tp === 'REMOVABLE' ? 'Removible' : 'Opcional';
                  return (
                    <TouchableOpacity key={tp} style={[s.ingPickerChip, selected && s.ingPickerChipActive]} onPress={() => {
                      const next = [...prodIngredients];
                      next[idx] = { ...next[idx], type: tp };
                      setProdIngredients(next);
                    }}>
                      <Text style={[s.ingPickerChipText, selected && s.ingPickerChipTextActive]}>{label}</Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.ingPickerScroll}>
                {(ingredients || []).map(ing => {
                  const id = ing.id || ing.ingredientId;
                  const name = ing.name || ing.ingredientName;
                  const selected = String(pi.ingredientId || '') === String(id);
                  return (
                    <TouchableOpacity key={id} style={[s.ingPickerChip, selected && s.ingPickerChipActive]} onPress={() => {
                      const next = [...prodIngredients];
                      next[idx] = { ...next[idx], ingredientId: id, ingredientName: name };
                      setProdIngredients(next);
                    }}>
                      <Text style={[s.ingPickerChipText, selected && s.ingPickerChipTextActive]}>{name}</Text>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
            </View>
          ))}
          <TouchableOpacity style={[s.actionBtn, { marginBottom: 12 }]} onPress={() => setProdIngredients(prev => [...prev, { ingredientName: '', type: 'BASE', quantityRequired: '1', extraPrice: '0' }])}>
            <Ionicons name="add-circle" size={18} color="#22c55e" />
            <Text style={s.actionBtnText}>Agregar ingrediente</Text>
          </TouchableOpacity>

          <TouchableOpacity style={s.modalBtn} onPress={handleSaveProduct}><Text style={s.modalBtnText}>Guardar</Text></TouchableOpacity>
        </>
      ))}
      {renderFormModal(ingredientFormVisible, setIngredientFormVisible, editIng ? 'Editar Ingrediente' : 'Nuevo Ingrediente', (
        <>
          <TextInput style={s.input} placeholder="Nombre" placeholderTextColor="#64748b" value={ingName} onChangeText={setIngName} />
          <TextInput style={s.input} placeholder="Unidad (gramos, ml, unidad...)" placeholderTextColor="#64748b" value={ingUnit} onChangeText={setIngUnit} />
          <TouchableOpacity style={s.modalBtn} onPress={handleSaveIngredient}><Text style={s.modalBtnText}>Guardar</Text></TouchableOpacity>
        </>
      ))}
      <Modal visible={editModalVisible} transparent animationType="slide" onRequestClose={() => setEditModalVisible(false)}>
        <View style={s.modalOverlay}>
          <View style={s.modalContent}>
            <Text style={s.modalTitle}>Editar Stock</Text>
            <Text style={s.modalSubtitle}>{editIngredient?.ingredientName}</Text>
            <TextInput style={s.input} keyboardType="numeric" value={editQty} onChangeText={setEditQty} placeholder="Cantidad" placeholderTextColor="#64748b" />
            <TouchableOpacity style={s.modalBtn} onPress={handleSetStock}><Text style={s.modalBtnText}>Guardar</Text></TouchableOpacity>
            <TouchableOpacity style={[s.modalBtn, { backgroundColor: '#334155' }]} onPress={() => setEditModalVisible(false)}><Text style={s.modalBtnText}>Cancelar</Text></TouchableOpacity>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f1a' },
  connection: { alignSelf: 'flex-end', marginTop: 8, marginRight: 16 },
  tabs: { flexDirection: 'row', padding: 8, backgroundColor: '#1a1a2e', borderBottomWidth: 1, borderBottomColor: '#2d2d44' },
  tabBtn: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 8 },
  tabBtnActive: { backgroundColor: '#f59e0b' },
  tabBtnText: { fontSize: 12, fontWeight: '700', color: '#94a3b8', textAlign: 'center' },
  tabBtnTextActive: { color: '#0f0f1a' },
  tabContent: { flex: 1, padding: 12 },
  sectionTitle: { fontSize: 18, fontWeight: '800', color: '#f59e0b', marginBottom: 12, marginTop: 8 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  addBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#f59e0b', paddingHorizontal: 12, paddingVertical: 8, borderRadius: 10 },
  addBtnText: { color: '#0f0f1a', fontWeight: '800' },
  ingredientAdminRow: { flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: '#1a1a2e', borderRadius: 12, padding: 12, marginBottom: 10, borderWidth: 1, borderColor: '#2d2d44' },
  metricsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 16 },
  metricCard: { backgroundColor: '#1a1a2e', borderRadius: 12, padding: 12, width: '31%', borderLeftWidth: 4 },
  metricValue: { fontSize: 18, fontWeight: '800', color: '#e2e8f0', marginTop: 6 },
  metricLabel: { fontSize: 12, color: '#94a3b8', marginTop: 2 },

  // Table cards
  tableCard: { backgroundColor: '#1a1a2e', borderRadius: 16, padding: 16, marginBottom: 12, borderWidth: 1, borderColor: '#2d2d44' },
  tableCardBusy: { borderColor: '#f59e0b' },
  tableCardFree: { borderColor: '#166534', opacity: 0.92 },
  tableCardEmpty: { borderColor: '#22d3ee', borderWidth: 2 },
  tableCardDraft: { borderColor: '#fb923c', borderWidth: 2 },
  tableCardHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  tableCardTitle: { fontSize: 18, fontWeight: '800', color: '#e2e8f0' },
  tableMeta: { fontSize: 12, color: '#94a3b8', marginTop: 2 },
  tableHint: { fontSize: 12, color: '#64748b', marginTop: 6 },
  tableEmptyLine: { color: '#64748b', fontStyle: 'italic', marginTop: 8 },
  tableOrderBlock: { marginTop: 8, paddingTop: 8, borderTopWidth: 1, borderTopColor: '#2d2d44' },
  flowBadge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4, borderWidth: 1 },
  flowBadgeText: { fontSize: 12, fontWeight: '700' },
  tableOrderRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 6 },
  tableOrderInfo: { flex: 1, marginLeft: 10, fontSize: 14, color: '#e2e8f0' },
  tableOrderTotal: { fontSize: 14, fontWeight: '700', color: '#22c55e' },
  tableItemRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 4, paddingLeft: 8 },
  tableItemQty: { width: 28, fontSize: 13, fontWeight: '700', color: '#f59e0b' },
  tableItemName: { flex: 1, fontSize: 13, color: '#cbd5e1' },
  tableItemPrice: { fontSize: 13, color: '#94a3b8' },
  floorSummary: { color: '#94a3b8', marginBottom: 12, marginTop: -4 },

  // Status
  statusBadge: { borderRadius: 8, paddingHorizontal: 8, paddingVertical: 3, borderWidth: 1 },
  statusBadgeText: { fontSize: 11, fontWeight: '700' },

  // Reports
  summaryGrid: { flexDirection: 'row', gap: 10, marginBottom: 16 },
  summaryCard: { flex: 1, borderRadius: 16, padding: 16, alignItems: 'center' },
  summaryValue: { fontSize: 20, fontWeight: '800', color: '#e2e8f0', marginTop: 8 },
  summaryLabel: { fontSize: 12, color: '#94a3b8', marginTop: 4 },
  changeText: { fontSize: 11, fontWeight: '700', marginTop: 6 },
  changeMuted: { fontSize: 11, color: '#64748b', marginTop: 6, textAlign: 'center' },
  mixBar: { flexDirection: 'row', height: 18, borderRadius: 9, overflow: 'hidden', marginBottom: 12, backgroundColor: '#1e1e35' },
  legendRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  legendDot: { width: 10, height: 10, borderRadius: 5, marginRight: 8 },
  legendLabel: { flex: 1, fontSize: 13, color: '#e2e8f0' },
  legendPct: { width: 40, fontSize: 12, fontWeight: '700', color: '#94a3b8' },
  legendVal: { fontSize: 12, color: '#cbd5e1', textAlign: 'right' },
  periodRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  periodBtn: { flex: 1, alignItems: 'center', paddingVertical: 10, borderRadius: 10, backgroundColor: '#1a1a2e' },
  periodActive: { backgroundColor: '#f59e0b' },
  periodText: { fontSize: 14, fontWeight: '700', color: '#94a3b8' },
  periodTextActive: { color: '#0f0f1a' },
  distRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 8 },
  distCount: { fontSize: 14, fontWeight: '700', color: '#e2e8f0' },
  exportRow: { flexDirection: 'row', gap: 10, marginBottom: 20 },
  exportBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: 12, paddingVertical: 12 },
  exportBtnText: { fontSize: 14, fontWeight: '700', color: '#e2e8f0' },
  exportHint: { fontSize: 12, color: '#64748b', marginBottom: 10, marginTop: -4 },
  chartRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 10 },
  chartLabel: { width: 92, fontSize: 12, color: '#cbd5e1' },
  chartTrack: { flex: 1, height: 10, backgroundColor: '#1e1e35', borderRadius: 6, overflow: 'hidden', marginHorizontal: 8 },
  chartFill: { height: 10, borderRadius: 6 },
  chartValue: { width: 92, fontSize: 11, color: '#94a3b8', textAlign: 'right' },
  dayChart: { flexDirection: 'row', alignItems: 'flex-end', minHeight: 150, paddingVertical: 8, marginBottom: 8 },
  dayBarCol: { flex: 1, alignItems: 'center', justifyContent: 'flex-end' },
  dayBar: { width: 10, backgroundColor: '#f59e0b', borderRadius: 4, minHeight: 8 },
  dayBarLabel: { fontSize: 9, color: '#64748b', marginTop: 4 },
  dayBarAmount: { fontSize: 8, color: '#94a3b8', marginBottom: 4 },
  fullChart: { flexDirection: 'row', alignItems: 'flex-end', minHeight: 180, width: '100%', marginBottom: 16, paddingTop: 4 },
  fullBarCol: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', minWidth: 0, paddingHorizontal: 2 },
  fullBar: { width: '70%', backgroundColor: '#f59e0b', borderTopLeftRadius: 6, borderTopRightRadius: 6, minHeight: 16 },
  fullBarAmount: { fontSize: 11, fontWeight: '800', color: '#e2e8f0', marginBottom: 2 },
  fullBarOrders: { fontSize: 10, color: '#94a3b8', marginBottom: 4 },
  fullBarLabel: { fontSize: 11, fontWeight: '700', color: '#cbd5e1', marginTop: 6 },

  // Menu
  actionRow: { flexDirection: 'row', gap: 8, marginBottom: 12 },
  actionBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, backgroundColor: '#1a1a2e', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8, borderWidth: 1, borderColor: '#2d2d44' },
  actionBtnText: { fontSize: 14, fontWeight: '700', color: '#e2e8f0' },
  catSection: { marginBottom: 16 },
  catHeader: { backgroundColor: '#f59e0b', borderRadius: 10, paddingVertical: 8, paddingHorizontal: 14, marginBottom: 8 },
  catTitle: { fontSize: 16, fontWeight: '800', color: '#0f0f1a' },
  prodCard: { backgroundColor: '#1a1a2e', borderRadius: 12, padding: 12, marginBottom: 8, borderWidth: 1, borderColor: '#2d2d44' },
  prodCardInactive: { opacity: 0.6, borderColor: '#ef4444' },
  prodRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  prodName: { fontSize: 15, fontWeight: '700', color: '#e2e8f0' },
  prodNameInactive: { color: '#64748b' },
  prodPrice: { fontSize: 14, color: '#22c55e', fontWeight: '700', marginTop: 2 },

  // Top products
  topProdRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#1e1e35' },
  topProdRank: { width: 28, fontSize: 16, fontWeight: '800', color: '#f59e0b' },
  topProdName: { fontSize: 15, color: '#e2e8f0', fontWeight: '600' },
  topProdStats: { fontSize: 12, color: '#94a3b8', marginTop: 2 },

  // Activity
  activityRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#1e1e35' },
  activityText: { fontSize: 14, color: '#e2e8f0' },
  activityTime: { fontSize: 12, color: '#64748b', marginTop: 2 },
  activityTotal: { fontSize: 14, fontWeight: '700', color: '#22c55e' },

  // Inventory
  invRow: { backgroundColor: '#0f172a', borderRadius: 12, padding: 12, marginBottom: 8, flexDirection: 'row', alignItems: 'center' },
  invCard: { backgroundColor: '#0f172a', borderRadius: 12, padding: 12, marginBottom: 8 },
  invCardLow: { borderColor: '#ef4444', borderWidth: 1 },
  invDetail: { color: '#94a3b8', fontSize: 12, marginTop: 2 },
  invUnit: { color: '#94a3b8', fontSize: 12, marginTop: 2 },
  invName: { fontSize: 15, color: '#e2e8f0', fontWeight: '600' },
  invAlert: { fontSize: 13, color: '#ef4444', fontWeight: '700' },
  invActions: { flexDirection: 'row', gap: 6, marginLeft: 8 },
  invBtn: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6 },
  invBtnGreen: { backgroundColor: '#22c55e' },
  invBtnBlue: { backgroundColor: '#3b82f6' },
  invBtnRed: { backgroundColor: '#ef4444' },
  invBtnText: { color: '#fff', fontSize: 12, fontWeight: '700' },

  // Modal
  modalOverlay: { flex: 1, backgroundColor: '#000000cc', justifyContent: 'flex-end' },
  modalContent: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, maxHeight: '92%' },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  modalTitle: { fontSize: 20, fontWeight: '800', color: '#f59e0b', flex: 1, textAlign: 'center' },
  modalSubtitle: { fontSize: 14, color: '#94a3b8', marginBottom: 12 },
  modalClose: { fontSize: 24, color: '#64748b', padding: 4 },
  modalDesc: { fontSize: 14, color: '#94a3b8', marginBottom: 8 },
  modalPrice: { fontSize: 20, fontWeight: '700', color: '#e2e8f0', marginBottom: 12 },
  modalBadgeRow: { flexDirection: 'row', marginBottom: 16 },
  badge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4 },
  badgeActive: { backgroundColor: '#22c55e33' },
  badgeInactive: { backgroundColor: '#ef444433' },
  badgeText: { fontSize: 12, fontWeight: '700', color: '#e2e8f0' },
  ingRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#2d2d44' },
  ingRowUnavailable: { backgroundColor: '#2d151522' },
  ingRowLow: { backgroundColor: '#3d2e1a22' },
  ingName: { fontSize: 15, color: '#e2e8f0', fontWeight: '600' },
  ingType: { fontSize: 12, color: '#64748b', marginTop: 2 },
  ingStatusCol: { alignItems: 'flex-end' },
  ingStatusOk: { fontSize: 13, color: '#22c55e', fontWeight: '700' },
  ingStatusLow: { fontSize: 13, color: '#f59e0b', fontWeight: '700' },
  ingStatusUnavailable: { fontSize: 13, color: '#ef4444', fontWeight: '700' },
  ingDepleteBtn: { marginTop: 6, backgroundColor: '#ef4444', borderRadius: 8, paddingHorizontal: 10, paddingVertical: 4 },
  ingDepleteText: { color: '#fff', fontSize: 11, fontWeight: '700' },
  prodIngCard: { backgroundColor: '#0f172a', borderRadius: 12, padding: 10, marginBottom: 10, borderWidth: 1, borderColor: '#2d2d44' },
  ingPickerScroll: { marginTop: 2 },
  ingPickerChip: { backgroundColor: '#1e293b', borderRadius: 16, paddingHorizontal: 10, paddingVertical: 6, marginRight: 6, borderWidth: 1, borderColor: '#334155' },
  ingPickerChipActive: { backgroundColor: '#f59e0b', borderColor: '#f59e0b' },
  ingPickerChipText: { color: '#e2e8f0', fontSize: 12, fontWeight: '700' },
  ingPickerChipTextActive: { color: '#0f0f1a' },
  input: { backgroundColor: '#0f0f1a', color: '#e2e8f0', borderRadius: 10, padding: 12, fontSize: 16, borderWidth: 1, borderColor: '#2d2d44', marginBottom: 12 },
  modalBtn: { backgroundColor: '#f59e0b', borderRadius: 12, paddingVertical: 12, alignItems: 'center', marginBottom: 8 },
  modalBtnText: { color: '#0f0f1a', fontSize: 16, fontWeight: '700' },
});
