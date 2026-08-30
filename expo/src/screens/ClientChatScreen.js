import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, StyleSheet, Alert,
  ActivityIndicator, KeyboardAvoidingView, Keyboard, Platform, Modal, ScrollView, Vibration,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { getMenuWithStock } from '../api/inventoryApi';
import { callWaiter, requestPaymentBySession, confirmOrderForSession, clearDraftItems, cancelItemById, getCurrentTableOrder } from '../api/orderApi';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAuth } from '../hooks/useAuth';
import { useChat } from '../hooks/useChat';
import MessageBubble from '../components/MessageBubble';
import ConnectionIndicator from '../components/ConnectionIndicator';
import { formatCOP } from '../utils/currency';
import { formatItemName, findProductInMenu, buildCartIngredientRows } from '../utils/orderFormatting';

const stripPrices = (text) => {
  if (!text) return text;
  return text
    .replace(/\s*[-\u2013\u2014]\s*\$[\d.,]+/g, '')
    .replace(/\s*\(\$[\d.,]+\)/g, '')
    .replace(/\s*[-\u2013\u2014]\s*COP\s+[\d.,]+/gi, '')
    .replace(/\(COP\s+[\d.,]+\)/gi, '')
    .replace(/COP\s+[\d.,]+/gi, '')
    .replace(/\$[\d.,]+/g, '')
    .replace(/ {2,}/g, ' ')
    .trim();
};

export default function ClientChatScreen({ route, navigation }) {
  const { tableId, sessionId } = route.params;
  const insets = useSafeAreaInsets();
  const { subscribe, connected } = useWebSocket();
  const { endSession, startSession } = useAuth();
  const [activeSessionId, setActiveSessionId] = useState(sessionId);
  const { sendTurn, loadTurns } = useChat(tableId, activeSessionId, null);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [orderSnapshot, setOrderSnapshot] = useState(null);
  const [showOrder, setShowOrder] = useState(false);
  const [confirmModal, setConfirmModal] = useState(null);
  const [menuData, setMenuData] = useState([]);
  const [menuModalVisible, setMenuModalVisible] = useState(false);
  const [productModalVisible, setProductModalVisible] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [productQty, setProductQty] = useState(1);
  const [removedIngredients, setRemovedIngredients] = useState([]);
  const [addedIngredients, setAddedIngredients] = useState([]);
  const [expandedCartItemId, setExpandedCartItemId] = useState(null);
  const [keyboardHeight, setKeyboardHeight] = useState(0);
  const flatListRef = useRef();
  const msgCounter = useRef(0);

  // Resetear todo el estado cuando cambia la sesión (nuevo cliente en la mesa)
  useEffect(() => {
    setMessages([]);
    setInput('');
    setOrderSnapshot(null);
    setShowOrder(false);
    setConfirmModal(null);
    setSelectedProduct(null);
    setProductQty(1);
    setRemovedIngredients([]);
    setAddedIngredients([]);
    setExpandedCartItemId(null);
    msgCounter.current = 0;
    setActiveSessionId(sessionId);
  }, [sessionId]);

  useEffect(() => {
    const showEvent = Platform.OS === 'ios' ? 'keyboardWillShow' : 'keyboardDidShow';
    const hideEvent = Platform.OS === 'ios' ? 'keyboardWillHide' : 'keyboardDidHide';
    const onShow = Keyboard.addListener(showEvent, (e) => {
      setKeyboardHeight(e?.endCoordinates?.height || 0);
    });
    const onHide = Keyboard.addListener(hideEvent, () => setKeyboardHeight(0));
    return () => {
      onShow.remove();
      onHide.remove();
    };
  }, []);

  const adoptSnapshotSession = useCallback(async (snap) => {
    if (!snap?.sessionId || snap.sessionId === activeSessionId) return;
    setActiveSessionId(snap.sessionId);
    await startSession({ sessionId: snap.sessionId, tableId: snap.tableId || tableId, status: 'ACTIVE' });
  }, [activeSessionId, startSession, tableId]);

  const setVisibleOrderSnapshot = useCallback((snap) => {
    const hasItems = (snap?.items || []).length > 0;
    setOrderSnapshot(snap && snap.status !== 'CANCELLED' && snap.status !== 'PAID' && hasItems ? snap : null);
  }, []);

  const resetServiceAndExit = useCallback(async (message = 'El mesero ha cerrado tu servicio. ¡Gracias por tu visita!') => {
    setOrderSnapshot(null);
    setMessages([]);
    setInput('');
    setConfirmModal(null);
    setShowOrder(false);
    setSelectedProduct(null);
    setProductQty(1);
    setRemovedIngredients([]);
    setAddedIngredients([]);
    await endSession();
    Alert.alert('Servicio finalizado', message);
    navigation.replace('Login');
  }, [endSession, navigation]);

  useEffect(() => {
    // Cargar pedido actual al entrar (sesión persistente)
    loadActiveOrder();
    loadHistory();
    const sub1 = subscribe(`/topic/table/${tableId}/chat`, (msg) => {
      if (msg.type === 'ASSISTANT_MESSAGE' && msg.orderSnapshot) {
        const snap = msg.orderSnapshot;
        setVisibleOrderSnapshot(snap);
        adoptSnapshotSession(snap);
      }
    });
    const sub2 = subscribe(`/topic/table/${tableId}/orders`, (msg) => {
      if (msg.type === 'ORDER_UPDATED') {
        if (msg.orderSnapshot) {
          setVisibleOrderSnapshot(msg.orderSnapshot);
          adoptSnapshotSession(msg.orderSnapshot);
        }
        if (msg.status === 'PAID') {
          resetServiceAndExit('Tu pedido fue cobrado. La mesa queda lista para un nuevo cliente.');
          return;
        }
        if (msg.status === 'CANCELLED') {
          setOrderSnapshot(null);
        } else if (msg.status && msg.status !== 'DRAFT') {
          loadActiveOrder();
        }
        const notifMap = {
          PENDING: '📨 Tu pedido fue enviado a cocina.',
          IN_PREPARATION: '🍳 Tu pedido está en preparación. ¡Ya casi está listo!',
          READY: '✅ ¡Tu pedido está listo! El mesero te lo llevará en un momento.',
          DELIVERED: '🍽️ Tu pedido ha sido entregado. ¡Buen provecho! Puedes solicitar la cuenta cuando quieras.',
          PAYMENT_REQUESTED: '🧧 Cuenta solicitada. Un mesero se acercará pronto.',
          CANCELLED: '❌ Tu pedido ha sido cancelado.',
        };
        if (notifMap[msg.status]) {
          Vibration.vibrate(200);
          const n = { id: `notif-${Date.now()}`, role: 'assistant', content: notifMap[msg.status], timestamp: new Date().toISOString() };
          setMessages(prev => [...prev, n]);
        }
      }
      if (msg.type === 'SESSION_CLOSED') {
        resetServiceAndExit();
      }
    });
    const sub3 = subscribe('/topic/menu/updates', (msg) => {
      if (msg.type === 'MENU_UPDATE') {
        const alertMsg = msg.available
          ? `✅ ${msg.productName} vuelve a estar disponible`
          : `❌ ${msg.productName} ya no está disponible`;
        const assistantMsg = { id: genId(), role: 'assistant', content: alertMsg, timestamp: new Date().toISOString() };
        setMessages(prev => [...prev, assistantMsg]);
      }
    });
    return () => {
      if (sub1) sub1();
      if (sub2) sub2();
      if (sub3) sub3();
    };
  }, [subscribe, adoptSnapshotSession, resetServiceAndExit]);

  const loadActiveOrder = async () => {
    try {
      const snap = await getCurrentTableOrder(tableId);
      if (snap) {
        setVisibleOrderSnapshot(snap);
        await adoptSnapshotSession(snap);
      }
      return snap;
    } catch (e) { /* ignore network errors */ }
    return null;
  };

  const handleConfirmOrder = async () => {
    if (!orderSnapshot?.orderId) return;
    setShowOrder(false);
    setLoading(true);
    try {
      const snap = await confirmOrderForSession(orderSnapshot.orderId, activeSessionId);
      setVisibleOrderSnapshot(snap);
      await adoptSnapshotSession(snap);
      const msg = { id: genId(), role: 'assistant', content: '\u2705 \u00a1Pedido confirmado! Tu orden ya fue enviada a cocina. Te avisaremos cuando est\u00e9 lista.', timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, msg]);
    } catch (e) {
      Alert.alert('Error', 'No se pudo confirmar: ' + e.message);
    }
    setLoading(false);
  };

  const handleClearDraft = () => {
    if (!orderSnapshot?.orderId) return;
    if (orderSnapshot.status !== 'DRAFT') {
      Alert.alert('No disponible', 'Solo puedes borrar el pedido mientras está en borrador.');
      return;
    }
    Alert.alert('Vaciar pedido', '¿Quieres borrar todos los productos del pedido?', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Vaciar', style: 'destructive', onPress: async () => {
          setLoading(true);
          try {
            const snap = await clearDraftItems(orderSnapshot.orderId, activeSessionId);
            setVisibleOrderSnapshot(snap);
            await adoptSnapshotSession(snap);
          } catch (e) {
            Alert.alert('Error', e.message);
          }
          setLoading(false);
        },
      },
    ]);
  };

  const handleCancelItem = (item) => {
    if (!orderSnapshot?.orderId || !item?.itemId) return;
    if (orderSnapshot.status !== 'DRAFT') {
      Alert.alert('No disponible', 'Solo puedes cancelar productos mientras el pedido está en borrador.');
      return;
    }
    const qty = item.quantity || 1;
    if (qty <= 1) {
      Alert.alert('Quitar producto', `¿Quieres quitar ${formatItemName(item)}?`, [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Quitar', style: 'destructive', onPress: async () => {
            setLoading(true);
            try {
              const snap = await cancelItemById(orderSnapshot.orderId, item.itemId, 1, activeSessionId);
              setVisibleOrderSnapshot(snap);
              await adoptSnapshotSession(snap);
            } catch (e) {
              Alert.alert('Error', e.message);
            }
            setLoading(false);
          },
        },
      ]);
      return;
    }
    Alert.alert('Quitar producto', `¿Cuántos quieres quitar de ${formatItemName(item)}?`, [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Quitar 1', onPress: async () => {
          setLoading(true);
          try {
            const snap = await cancelItemById(orderSnapshot.orderId, item.itemId, 1, activeSessionId);
            setVisibleOrderSnapshot(snap);
            await adoptSnapshotSession(snap);
          } catch (e) { Alert.alert('Error', e.message); }
          setLoading(false);
        },
      },
      {
        text: `Quitar ${qty}`, style: 'destructive', onPress: async () => {
          setLoading(true);
          try {
            const snap = await cancelItemById(orderSnapshot.orderId, item.itemId, qty, activeSessionId);
            setVisibleOrderSnapshot(snap);
            await adoptSnapshotSession(snap);
          } catch (e) { Alert.alert('Error', e.message); }
          setLoading(false);
        },
      },
    ]);
  };

  const handleRequestPayment = async () => {
    if (!orderSnapshot?.orderId) return;
    setShowOrder(false);
    setLoading(true);
    try {
      const snap = await requestPaymentBySession(activeSessionId, tableId);
      setVisibleOrderSnapshot(snap);
      await adoptSnapshotSession(snap);
      const msg = { id: genId(), role: 'assistant', content: '\ud83d\udcb5 \u00a1Cuenta solicitada! Un mesero vendr\u00e1 a tu mesa para cobrar.', timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, msg]);
    } catch (e) {
      Alert.alert('Error', 'No se pudo solicitar la cuenta: ' + e.message);
    }
    setLoading(false);
  };

  const loadHistory = async () => {
    try {
      const history = await loadTurns();
      if (!Array.isArray(history) || history.length === 0) return;
      const mapped = history.map(t => ({
        id: t.id || genId(), role: t.role === 'USER' ? 'user' : 'assistant',
        content: t.role !== 'USER' ? stripPrices(t.content) : t.content,
        timestamp: t.createdAt,
      }));
      setMessages(mapped);
    } catch (e) { /* first time */ }
  };

  const genId = () => `msg-${Date.now()}-${++msgCounter.current}`;

  const handleSend = () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput('');
    sendText(text);
  };

  const handleConfirm = (option) => {
    setConfirmModal(null);
    sendText(option);
  };

  // ─── MENU FUNCTIONS ───
  const loadMenu = async () => {
    try {
      const data = await getMenuWithStock();
      setMenuData(data);
    } catch (e) { /* retry */ }
  };

  const openMenu = () => {
    loadMenu();
    setMenuModalVisible(true);
  };

  useEffect(() => {
    loadMenu();
  }, []);

  const openProductDetail = (product) => {
    setSelectedProduct(product);
    setProductQty(1);
    setRemovedIngredients([]);
    setAddedIngredients([]);
    setMenuModalVisible(false);
    setProductModalVisible(true);
  };

  const toggleRemoveIngredient = (ing) => {
    setRemovedIngredients(prev => {
      const exists = prev.find(r => r.id === ing.id);
      if (exists) return prev.filter(r => r.id !== ing.id);
      return [...prev, ing];
    });
  };

  const toggleAddIngredient = (ing) => {
    setAddedIngredients(prev => {
      const exists = prev.find(a => a.id === ing.id);
      if (exists) return prev.filter(a => a.id !== ing.id);
      return [...prev, ing];
    });
  };

  const addToOrder = () => {
    if (!selectedProduct) return;
    let text = `Quiero ${productQty} ${selectedProduct.name} (id:${selectedProduct.id})`;
    if (removedIngredients.length > 0) {
      text += ' sin ' + removedIngredients.map(i => i.name.toLowerCase()).join(', sin ');
    }
    if (addedIngredients.length > 0) {
      text += ' con ' + addedIngredients.map(i => i.name.toLowerCase()).join(', con ');
    }
    setProductModalVisible(false);
    sendText(text, 'MENU');
  };

  const sendText = async (text, source = null) => {
    if (!text || loading) return;
    const userMsg = { id: genId(), role: 'user', content: text, timestamp: new Date().toISOString() };
    setMessages(prev => [...prev, userMsg]);
    setLoading(true);
    try {
      const idempKey = `${activeSessionId}-${Date.now()}`;
      const response = await sendTurn(text, idempKey, source);
      const assistantContent = stripPrices(response.message);
      const assistantMsg = {
        id: response.turnId || genId(), role: 'assistant',
        content: assistantContent, timestamp: response.timestamp,
      };
      setMessages(prev => [...prev, assistantMsg]);
      if (response.orderSnapshot !== undefined) {
        const snap = response.orderSnapshot;
        setVisibleOrderSnapshot(snap);
        await adoptSnapshotSession(snap);
      } else if (/pedido.*cancelado|cancelado.*pedido|pedido ha sido cancelado/i.test(response.message || '')) {
        setOrderSnapshot(null);
      }
      if (response.confirmationRequest) setConfirmModal(response.confirmationRequest);
    } catch (e) {
      const errMsg = { id: genId(), role: 'assistant', content: '⚠️ Error de conexión. Intenta de nuevo.', timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, errMsg]);
    }
    setLoading(false);
  };

  const handleCallWaiter = () => {
    Alert.alert(
      '👨‍💼 Llamar Mesero',
      '¿Necesitas ayuda de un mesero?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Llamar', onPress: async () => {
            try {
              await callWaiter(activeSessionId);
              const msg = { id: genId(), role: 'assistant', content: '👨‍💼 Mesero notificado. En un momento te atenderá.', timestamp: new Date().toISOString() };
              setMessages(prev => [...prev, msg]);
            } catch (e) {
              Alert.alert('Error', 'No se pudo notificar al mesero: ' + e.message);
            }
          },
        },
      ]
    );
  };

  const getFreshOrderSnapshot = async () => {
    const fresh = await loadActiveOrder();
    return fresh || orderSnapshot;
  };

  const handleQuickRequestPayment = async () => {
    const currentOrder = await getFreshOrderSnapshot();
    if (!currentOrder?.orderId) {
      Alert.alert('Sin pedido', 'No tienes un pedido activo para solicitar la cuenta.');
      return;
    }
    if (currentOrder.status === 'DRAFT') {
      Alert.alert(
        '⚠️ Confirma tu pedido primero',
        'Debes confirmar tu pedido antes de solicitar la cuenta. Abre "Tu Pedido" y pulsa Confirmar.',
        [{ text: 'Entendido' }, { text: 'Abrir pedido', onPress: () => setShowOrder(true) }]
      );
      return;
    }
    if (['PENDING', 'IN_PREPARATION', 'READY'].includes(currentOrder.status)) {
      Alert.alert(
        '⏳ Pedido en proceso',
        'Tu pedido aún no ha sido entregado. Debes esperar a que el mesero te lo traiga antes de solicitar la cuenta.'
      );
      return;
    }
    if (currentOrder.status === 'PAYMENT_REQUESTED') {
      Alert.alert('Ya solicitado', 'Ya enviaste la solicitud de cuenta. Un mesero se acercará pronto.');
      return;
    }
    if (currentOrder.status === 'PAID') {
      Alert.alert('Pagado', '¡Tu pedido ya fue pagado! ¡Gracias por tu visita!');
      return;
    }
    Alert.alert(
      '🧾 Solicitar Cuenta',
      '¿Deseas solicitar la cuenta? Un mesero vendrá a tu mesa a cobrar.',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Solicitar', onPress: async () => {
            setLoading(true);
            try {
              const snap = await requestPaymentBySession(activeSessionId, tableId);
              setVisibleOrderSnapshot(snap);
              await adoptSnapshotSession(snap);
              const msg = { id: genId(), role: 'assistant', content: '💵 ¡Cuenta solicitada! Un mesero vendrá a tu mesa para cobrar.', timestamp: new Date().toISOString() };
              setMessages(prev => [...prev, msg]);
            } catch (e) {
              Alert.alert('Error', 'No se pudo solicitar la cuenta: ' + e.message);
            } finally {
              setLoading(false);
            }
          },
        },
      ]
    );
  };

  const handleCheckStatus = async () => {
    const currentOrder = await getFreshOrderSnapshot();
    if (!currentOrder) {
      const msg = { id: genId(), role: 'assistant', content: 'Aún no tienes un pedido activo. ¡Puedes escribirme lo que deseas pedir!', timestamp: new Date().toISOString() };
      setMessages(prev => [...prev, msg]);
      return;
    }
    const statusMap = { DRAFT: 'Borrador (no confirmado)', PENDING: 'Enviado a cocina ⏳', IN_PREPARATION: 'En preparación 🍳', READY: '¡Listo para entrega! ✅', DELIVERED: 'Entregado 🍽️', PAYMENT_REQUESTED: 'Cuenta solicitada 🧾', PAID: 'Pagado ✅' };
    const statusText = statusMap[currentOrder.status] || currentOrder.status;
    const itemsList = (currentOrder.items || []).map(it => `  • ${it.quantity}x ${formatItemName(it)}`).join('\n');
    const content = `📋 Estado de tu pedido:\n\n${statusText}\n\n${itemsList ? 'Productos:\n' + itemsList : ''}`;
    const msg = { id: genId(), role: 'assistant', content, timestamp: new Date().toISOString() };
    setMessages(prev => [...prev, msg]);
  };

  const renderMessage = useCallback(({ item }) => (
    <MessageBubble role={item.role} content={item.content} />
  ), []);

  const STATUS_MAP = { DRAFT: 'Borrador', PENDING: 'Enviado a cocina ⏳', IN_PREPARATION: 'En preparación 🍳', READY: '¡Listo! ✅', DELIVERED: 'Entregado 🍽️', PAYMENT_REQUESTED: 'Cuenta solicitada 🧾', PAID: 'Pagado ✅' };

  const renderOrderPanel = () => (
    <Modal visible={showOrder} animationType="slide" transparent onRequestClose={() => { setShowOrder(false); setExpandedCartItemId(null); }}>
      <View style={s.modalOverlay}>
        <View style={s.orderPanel}>
          <View style={s.orderHeader}>
            <Text style={s.orderTitle}>🛒 Tu Pedido</Text>
            <TouchableOpacity onPress={() => { setShowOrder(false); setExpandedCartItemId(null); }}>
              <Ionicons name="close" size={28} color="#f59e0b" />
            </TouchableOpacity>
          </View>
          <ScrollView>
          {orderSnapshot && (orderSnapshot.items || []).length > 0 ? (
            <>
              {(orderSnapshot.items || []).map((item, i) => {
                const itemKey = item.itemId || i;
                const expanded = expandedCartItemId === itemKey;
                const product = findProductInMenu(menuData, item.productId);
                const ingredientRows = (item.ingredients && item.ingredients.length)
                  ? item.ingredients
                  : buildCartIngredientRows(product, item.modifiers);
                return (
                  <View key={itemKey} style={s.orderItemBlock}>
                    <View style={s.orderItem}>
                      <TouchableOpacity
                        style={{ flex: 1 }}
                        onPress={() => setExpandedCartItemId(expanded ? null : itemKey)}
                        activeOpacity={0.75}
                      >
                        <View style={s.orderItemTitleRow}>
                          <Ionicons name={expanded ? 'chevron-down' : 'chevron-forward'} size={16} color="#94a3b8" />
                          <Text style={s.orderItemName}>{item.quantity}x {item.productName}</Text>
                        </View>
                        {item.notes ? <Text style={s.orderItemMods}>{item.notes}</Text> : null}
                        {!expanded && formatItemName(item) !== item.productName ? (
                          <Text style={s.orderItemMods}>{formatItemName(item).replace(`${item.productName} `, '')}</Text>
                        ) : null}
                      </TouchableOpacity>
                      {orderSnapshot.status === 'DRAFT' && (
                        <TouchableOpacity onPress={() => handleCancelItem(item)} style={s.orderItemRemove}>
                          <Ionicons name="trash-outline" size={18} color="#fca5a5" />
                        </TouchableOpacity>
                      )}
                      <Text style={s.orderItemPrice}>{formatCOP(item.lineTotal)}</Text>
                    </View>
                    {expanded && (
                      <View style={s.cartIngWrap}>
                        {ingredientRows.length === 0 ? (
                          <Text style={s.cartIngEmpty}>Este producto no tiene ingredientes registrados.</Text>
                        ) : ingredientRows.map((ing) => {
                          const statusLabel = ing.type === 'BASE' ? 'Obligatorio'
                            : ing.status === 'removed' ? 'Quitado'
                            : ing.status === 'added' ? 'Agregado'
                            : ing.status === 'available' ? 'Sin agregar'
                            : 'Incluido';
                          return (
                            <View key={ing.id} style={s.cartIngRow}>
                              <Text style={[
                                s.cartIngName,
                                ing.status === 'removed' && s.cartIngRemoved,
                                ing.status === 'added' && s.cartIngAdded,
                              ]}>{ing.name}</Text>
                              <Text style={[
                                s.cartIngStatus,
                                ing.status === 'removed' && s.cartIngStatusRemoved,
                                ing.status === 'added' && s.cartIngStatusAdded,
                              ]}>{statusLabel}</Text>
                            </View>
                          );
                        })}
                      </View>
                    )}
                  </View>
                );
              })}
              <View style={s.orderTotal}>
                <Text style={s.orderTotalLabel}>Total</Text>
                <Text style={s.orderTotalValue}>{formatCOP(orderSnapshot.total)}</Text>
              </View>
              <View style={s.statusRow}>
                <View style={[s.statusDot, { backgroundColor: orderSnapshot.status === 'READY' ? '#22c55e' : orderSnapshot.status === 'IN_PREPARATION' ? '#f59e0b' : orderSnapshot.status === 'PENDING' ? '#3b82f6' : '#64748b' }]} />
                <Text style={s.orderStatus}>{STATUS_MAP[orderSnapshot.status] || orderSnapshot.status}</Text>
              </View>
              {orderSnapshot.status === 'DRAFT' && (
                <TouchableOpacity style={s.confirmOrderBtn} onPress={handleConfirmOrder}>
                  <Ionicons name="checkmark-circle" size={22} color="#0f0f1a" />
                  <Text style={s.confirmOrderBtnText}>Confirmar Pedido</Text>
                </TouchableOpacity>
              )}
              {orderSnapshot.status === 'DRAFT' && (
                <TouchableOpacity style={s.clearOrderBtn} onPress={handleClearDraft}>
                  <Ionicons name="trash" size={20} color="#fff" />
                  <Text style={s.clearOrderBtnText}>Vaciar Pedido</Text>
                </TouchableOpacity>
              )}
              {orderSnapshot.status === 'DELIVERED' && (
                <TouchableOpacity style={[s.confirmOrderBtn, { backgroundColor: '#a855f7' }]} onPress={handleRequestPayment}>
                  <Ionicons name="cash" size={22} color="#fff" />
                  <Text style={[s.confirmOrderBtnText, { color: '#fff' }]}>Pedir la Cuenta</Text>
                </TouchableOpacity>
              )}
            </>
          ) : (
            <Text style={s.emptyOrder}>Tu pedido está vacío. Escribe lo que deseas pedir o usa el menú.</Text>
          )}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );

  return (
    <KeyboardAvoidingView
      style={s.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? insets.top + 64 : 0}
    >
      {renderOrderPanel()}

      {/* Confirmation Modal */}
      {confirmModal && (
        <Modal visible={true} transparent animationType="fade">
          <View style={s.modalOverlay}>
            <View style={s.confirmPanel}>
              <Text style={s.confirmTitle}>⚠️ Confirmación</Text>
              <Text style={s.confirmMsg}>{confirmModal.message}</Text>
              <View style={s.confirmBtns}>
                {confirmModal.options?.map((opt, i) => (
                  <TouchableOpacity key={i} style={[s.confirmBtn, i === 0 && s.confirmBtnPrimary]}
                    onPress={() => handleConfirm(opt)}>
                    <Text style={[s.confirmBtnText, i === 0 && s.confirmBtnTextPrimary]}>{opt}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </View>
          </View>
        </Modal>
      )}

      {/* Header con botón de pedido */}
      <View style={s.topBar}>
        <ConnectionIndicator connected={connected} />
        <TouchableOpacity style={s.orderBtn} onPress={() => { loadMenu(); setShowOrder(true); }}>
          <Ionicons name="cart" size={22} color="#f59e0b" />
          <Text style={s.orderBtnText}>
            {((orderSnapshot?.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0)) === 0
              ? 'Tu pedido'
              : `${(orderSnapshot.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0)} ${(orderSnapshot.items || []).reduce((sum, item) => sum + (item.quantity || 0), 0) === 1 ? 'producto' : 'productos'}`}
          </Text>
          {(orderSnapshot?.items || []).length > 0 && (
            <View style={s.statusPill}>
              <Text style={s.statusPillText}>{STATUS_MAP?.[orderSnapshot.status] || orderSnapshot?.status || ''}</Text>
            </View>
          )}
        </TouchableOpacity>
      </View>

      {/* Chat area — flex:1 para ocupar el espacio restante */}
      <View style={{ flex: 1 }}>
        <FlatList ref={flatListRef} data={messages} renderItem={renderMessage}
          keyExtractor={item => item.id} contentContainerStyle={s.chatList}
          onContentSizeChange={() => flatListRef.current?.scrollToEnd({ animated: true })}
          ListEmptyComponent={
            <View style={s.emptyChat}>
              <Text style={s.emptyChatEmoji}>👋</Text>
              <Text style={s.emptyChatTitle}>¡Bienvenido!</Text>
              <Text style={s.emptyChatText}>Soy tu asistente de mesa. Puedes pedirme lo que quieras del menú,
                hacer preguntas o solicitar la cuenta. ¡Estoy para ayudarte!</Text>
              <Text style={s.emptyChatHint}>Prueba: "¿Qué me recomiendas?" o "Quiero unos tacos al pastor"</Text>
            </View>
          }
        />

        {/* Typing Indicator */}
        {loading && (
          <View style={s.typingRow}>
            <ActivityIndicator size="small" color="#f59e0b" />
            <Text style={s.typingText}>Procesando...</Text>
          </View>
        )}
      </View>

      {/* Bottom bar: botones rápidos + input — siempre fijos arriba del teclado */}
      <View style={[s.bottomBar, { paddingBottom: 8 + (Platform.OS === 'ios' ? 0 : keyboardHeight) }]}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={s.quickActionsScroll} contentContainerStyle={s.quickActions}>
          <TouchableOpacity style={s.quickBtn} onPress={openMenu}>
            <Ionicons name="restaurant" size={16} color="#f59e0b" />
            <Text style={s.quickBtnText}>Menú</Text>
          </TouchableOpacity>
          <TouchableOpacity style={s.quickBtn} onPress={handleCheckStatus}>
            <Ionicons name="receipt-outline" size={16} color="#22c55e" />
            <Text style={s.quickBtnText}>Estado</Text>
          </TouchableOpacity>
          <TouchableOpacity style={s.quickBtn} onPress={handleCallWaiter}>
            <Ionicons name="person" size={16} color="#3b82f6" />
            <Text style={s.quickBtnText}>Mesero</Text>
          </TouchableOpacity>
          <TouchableOpacity style={s.quickBtn} onPress={handleQuickRequestPayment}>
            <Ionicons name="cash" size={16} color="#a855f7" />
            <Text style={s.quickBtnText}>Cuenta</Text>
          </TouchableOpacity>
        </ScrollView>

        <View style={s.inputBar}>
          <TextInput style={s.textInput} value={input} onChangeText={setInput}
            placeholder="Escribe tu pedido..."
            placeholderTextColor="#64748b" multiline maxLength={500}
            onSubmitEditing={handleSend} returnKeyType="send" />
          <TouchableOpacity style={[s.sendBtn, (!input.trim() || loading) && s.sendBtnDisabled]}
            onPress={handleSend} disabled={!input.trim() || loading}>
            <Ionicons name="send" size={22} color={input.trim() && !loading ? '#0f0f1a' : '#64748b'} />
          </TouchableOpacity>
        </View>
      </View>

      {/* Menu Modal */}
      <Modal visible={menuModalVisible} transparent animationType="slide" onRequestClose={() => setMenuModalVisible(false)}>
        <View style={s.modalOverlay}>
          <View style={s.menuPanel}>
            <View style={s.menuHeader}>
              <Text style={s.menuTitle}>📋 Menú</Text>
              <TouchableOpacity onPress={() => setMenuModalVisible(false)}>
                <Ionicons name="close" size={28} color="#f59e0b" />
              </TouchableOpacity>
            </View>
            <ScrollView>
              {menuData.map((cat) => (
                <View key={cat.id} style={s.menuCatSection}>
                  <Text style={s.menuCatTitle}>{cat.name}</Text>
                  <View style={s.menuGrid}>
                    {(cat.products?.filter(p => p.active) ?? []).map((p) => (
                      <TouchableOpacity key={p.id} style={s.menuCard} onPress={() => openProductDetail(p)}>
                        <Text style={s.menuCardName} numberOfLines={2}>{p.name}</Text>
                        <Text style={s.menuCardPrice}>{formatCOP(p.price ?? 0)}</Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </View>
              ))}
            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* Product Detail Modal */}
      <Modal visible={productModalVisible} transparent animationType="slide" onRequestClose={() => setProductModalVisible(false)}>
        <View style={s.modalOverlay}>
          <View style={s.productPanel}>
            <View style={s.menuHeader}>
              <Text style={s.menuTitle}>{selectedProduct?.name}</Text>
              <TouchableOpacity onPress={() => setProductModalVisible(false)}>
                <Ionicons name="close" size={28} color="#f59e0b" />
              </TouchableOpacity>
            </View>
            <ScrollView>
              <Text style={s.productDesc}>{selectedProduct?.description}</Text>
              <Text style={s.productPrice}>{formatCOP(selectedProduct?.price)}</Text>

              {/* Quantity selector */}
              <View style={s.qtyRow}>
                <TouchableOpacity style={s.qtyBtn} onPress={() => setProductQty(Math.max(1, productQty - 1))}>
                  <Text style={s.qtyBtnText}>-</Text>
                </TouchableOpacity>
                <Text style={s.qtyValue}>{productQty}</Text>
                <TouchableOpacity style={s.qtyBtn} onPress={() => setProductQty(productQty + 1)}>
                  <Text style={s.qtyBtnText}>+</Text>
                </TouchableOpacity>
              </View>

              {/* Ingredients */}
              <Text style={s.ingTitle}>🧂 Ingredientes</Text>
              {selectedProduct?.ingredients?.length === 0 && (
                <Text style={s.ingEmpty}>Sin ingredientes personalizables para este plato.</Text>
              )}
              {selectedProduct?.ingredients?.map((ing) => {
                const isBase = ing.type === 'BASE';
                const isRemovable = ing.type === 'REMOVABLE';
                const isOptional = ing.type === 'OPTIONAL';
                const isRemoved = removedIngredients.some(r => r.id === ing.id);
                const isAdded = addedIngredients.some(a => a.id === ing.id);
                return (
                  <View key={ing.id} style={[s.ingRowClient, !ing.isAvailable && s.ingRowUnavailable]}>
                    <View style={{ flex: 1 }}>
                      <Text style={s.ingNameClient}>{ing.name}</Text>
                      <Text style={s.ingTypeClient}>{ing.type} · {ing.unit}</Text>
                    </View>
                    {isBase ? (
                      <Text style={s.ingBadgeBase}>Obligatorio</Text>
                    ) : isRemovable ? (
                      <TouchableOpacity style={[s.ingToggle, isRemoved && s.ingToggleOff]} onPress={() => toggleRemoveIngredient(ing)}>
                        <Text style={s.ingToggleText}>{isRemoved ? 'Quitar ✓' : 'Quitar'}</Text>
                      </TouchableOpacity>
                    ) : isOptional ? (
                      <TouchableOpacity style={[s.ingToggle, isAdded && s.ingToggleOn]} onPress={() => toggleAddIngredient(ing)}>
                        <Text style={s.ingToggleText}>{isAdded ? 'Agregar ✓' : 'Agregar'}</Text>
                      </TouchableOpacity>
                    ) : null}
                  </View>
                );
              })}

              <TouchableOpacity style={s.addToOrderBtn} onPress={addToOrder}>
                <Text style={s.addToOrderText}>Agregar al pedido</Text>
              </TouchableOpacity>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </KeyboardAvoidingView>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0f0f1a' },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 8, paddingHorizontal: 16, borderBottomWidth: 1, borderBottomColor: '#1e1e35' },
  bottomBar: { backgroundColor: '#0f0f1a', borderTopWidth: 1, borderTopColor: '#1e1e35' },
  orderBtn: { flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: '#1a1a2e', borderRadius: 20, paddingHorizontal: 16, paddingVertical: 8 },
  orderBtnText: { color: '#f59e0b', fontSize: 14, fontWeight: '600' },
  chatList: { paddingHorizontal: 16, paddingVertical: 12 },
  msgRow: { marginBottom: 12, alignItems: 'flex-start' },
  msgRowUser: { alignItems: 'flex-end' },
  msgBubble: { maxWidth: '82%', borderRadius: 18, paddingHorizontal: 16, paddingVertical: 12 },
  userBubble: { backgroundColor: '#f59e0b', borderBottomRightRadius: 4 },
  assistantBubble: { backgroundColor: '#1a1a2e', borderBottomLeftRadius: 4, borderWidth: 1, borderColor: '#2d2d44' },
  botLabel: { fontSize: 11, color: '#64748b', marginBottom: 4 },
  msgText: { fontSize: 16, color: '#e2e8f0', lineHeight: 22 },
  userText: { color: '#0f0f1a' },
  typingRow: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 24, paddingVertical: 8 },
  typingText: { color: '#64748b', fontSize: 13 },
  inputBar: { flexDirection: 'row', alignItems: 'flex-end', paddingHorizontal: 12, paddingVertical: 10,
    borderTopWidth: 1, borderTopColor: '#1e1e35', backgroundColor: '#0f0f1a', gap: 8 },
  textInput: { flex: 1, backgroundColor: '#1a1a2e', borderRadius: 22, paddingHorizontal: 18, paddingVertical: 12,
    fontSize: 16, color: '#e2e8f0', maxHeight: 120, borderWidth: 1, borderColor: '#2d2d44' },
  sendBtn: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#f59e0b', alignItems: 'center', justifyContent: 'center' },
  sendBtnDisabled: { backgroundColor: '#2d2d44' },
  emptyChat: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 60, paddingHorizontal: 32 },
  emptyChatEmoji: { fontSize: 48, marginBottom: 16 },
  emptyChatTitle: { fontSize: 24, fontWeight: '700', color: '#f59e0b', marginBottom: 12 },
  emptyChatText: { fontSize: 16, color: '#94a3b8', textAlign: 'center', lineHeight: 24 },
  emptyChatHint: { fontSize: 14, color: '#64748b', textAlign: 'center', marginTop: 24, fontStyle: 'italic' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.7)', justifyContent: 'flex-end' },
  orderPanel: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, maxHeight: '70%' },
  orderHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 },
  orderTitle: { fontSize: 22, fontWeight: '700', color: '#f59e0b', flex: 1, textAlign: 'center' },
  orderItemBlock: { borderBottomWidth: 1, borderBottomColor: '#2d2d44' },
  orderItem: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', paddingVertical: 12 },
  orderItemTitleRow: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingRight: 8 },
  orderItemName: { fontSize: 16, color: '#e2e8f0', flex: 1, fontWeight: '600' },
  orderItemMods: { fontSize: 12, color: '#64748b', marginTop: 2, fontStyle: 'italic', marginLeft: 22 },
  orderItemPrice: { fontSize: 16, color: '#f59e0b', fontWeight: '600', marginLeft: 8 },
  cartIngWrap: { backgroundColor: '#12122a', borderRadius: 12, paddingHorizontal: 12, paddingVertical: 8, marginBottom: 10, marginLeft: 22 },
  cartIngRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 6 },
  cartIngName: { fontSize: 13, color: '#cbd5e1', flex: 1, paddingRight: 8 },
  cartIngRemoved: { color: '#fca5a5', textDecorationLine: 'line-through' },
  cartIngAdded: { color: '#86efac', fontWeight: '700' },
  cartIngStatus: { fontSize: 11, color: '#64748b', fontWeight: '700' },
  cartIngStatusRemoved: { color: '#f87171' },
  cartIngStatusAdded: { color: '#22c55e' },
  cartIngEmpty: { fontSize: 12, color: '#64748b', fontStyle: 'italic', paddingVertical: 6 },
  orderTotal: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 16, marginTop: 8 },
  orderTotalLabel: { fontSize: 20, fontWeight: '700', color: '#e2e8f0' },
  orderTotalValue: { fontSize: 20, fontWeight: '800', color: '#f59e0b' },
  statusRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, marginVertical: 8 },
  statusDot: { width: 10, height: 10, borderRadius: 5 },
  orderStatus: { textAlign: 'center', color: '#94a3b8', fontSize: 15, fontWeight: '600' },
  statusPill: { backgroundColor: '#2d2d44', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 2 },
  statusPillText: { fontSize: 11, color: '#94a3b8', fontWeight: '600' },
  emptyOrder: { color: '#64748b', textAlign: 'center', paddingVertical: 40, fontSize: 16 },
  confirmPanel: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24 },
  confirmTitle: { fontSize: 20, fontWeight: '700', color: '#f59e0b', textAlign: 'center', marginBottom: 12 },
  confirmMsg: { fontSize: 16, color: '#e2e8f0', textAlign: 'center', marginBottom: 24, lineHeight: 22 },
  confirmBtns: { gap: 12 },
  confirmBtn: { borderRadius: 14, padding: 16, alignItems: 'center', borderWidth: 1, borderColor: '#2d2d44' },
  confirmBtnPrimary: { backgroundColor: '#f59e0b', borderColor: '#f59e0b' },
  confirmBtnText: { fontSize: 16, fontWeight: '600', color: '#e2e8f0' },
  confirmBtnTextPrimary: { color: '#0f0f1a' },
  confirmOrderBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: '#22c55e', borderRadius: 14, padding: 16, marginTop: 16 },
  confirmOrderBtnText: { fontSize: 18, fontWeight: '700', color: '#0f0f1a' },
  clearOrderBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, backgroundColor: '#ef4444', borderRadius: 14, padding: 14, marginTop: 10 },
  clearOrderBtnText: { fontSize: 16, fontWeight: '700', color: '#fff' },
  orderItemRemove: { paddingHorizontal: 10, paddingTop: 2 },
  quickActionsScroll: { backgroundColor: '#0f0f1a', height: 52 },
  quickActions: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 12 },
  quickBtn: { flexDirection: 'row', alignItems: 'center', gap: 5, backgroundColor: '#1e293b', borderRadius: 20, paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderColor: '#334155' },
  quickBtnText: { color: '#f1f5f9', fontSize: 13, fontWeight: '700', flexShrink: 0 },
  // Menu modal
  menuPanel: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, maxHeight: '85%' },
  menuHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  menuTitle: { fontSize: 22, fontWeight: '800', color: '#f59e0b' },
  menuCatSection: { marginBottom: 20 },
  menuCatTitle: { fontSize: 18, fontWeight: '700', color: '#e2e8f0', marginBottom: 10 },
  menuGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  menuCard: { width: '47%', backgroundColor: '#0f0f1a', borderRadius: 14, padding: 14, borderWidth: 1, borderColor: '#2d2d44', minHeight: 90 },
  menuCardName: { fontSize: 14, fontWeight: '600', color: '#e2e8f0', marginBottom: 8, flex: 1 },
  menuCardPrice: { fontSize: 14, fontWeight: '700', color: '#f59e0b' },
  // Product modal
  productPanel: { backgroundColor: '#1a1a2e', borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 20, maxHeight: '85%' },
  productDesc: { fontSize: 14, color: '#94a3b8', marginBottom: 8 },
  productPrice: { fontSize: 24, fontWeight: '800', color: '#f59e0b', marginBottom: 16 },
  qtyRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 20, marginBottom: 20 },
  qtyBtn: { width: 44, height: 44, borderRadius: 22, backgroundColor: '#f59e0b', alignItems: 'center', justifyContent: 'center' },
  qtyBtnText: { fontSize: 24, fontWeight: '700', color: '#0f0f1a' },
  qtyValue: { fontSize: 22, fontWeight: '700', color: '#e2e8f0', minWidth: 40, textAlign: 'center' },
  ingTitle: { fontSize: 16, fontWeight: '700', color: '#e2e8f0', marginBottom: 10, marginTop: 4 },
  ingRowClient: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingVertical: 10, borderBottomWidth: 1, borderBottomColor: '#2d2d44' },
  ingRowUnavailable: { opacity: 0.4 },
  ingNameClient: { fontSize: 15, color: '#e2e8f0', fontWeight: '600' },
  ingTypeClient: { fontSize: 12, color: '#64748b', marginTop: 2 },
  ingBadgeBase: { fontSize: 12, color: '#22c55e', fontWeight: '700', backgroundColor: '#22c55e22', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 },
  ingToggle: { backgroundColor: '#334155', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 6 },
  ingToggleOn: { backgroundColor: '#22c55e' },
  ingToggleOff: { backgroundColor: '#ef4444' },
  ingToggleText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  ingEmpty: { fontSize: 14, color: '#64748b', fontStyle: 'italic', paddingVertical: 12, textAlign: 'center' },
  addToOrderBtn: { backgroundColor: '#f59e0b', borderRadius: 14, padding: 16, alignItems: 'center', marginTop: 20, marginBottom: 12 },
  addToOrderText: { fontSize: 18, fontWeight: '800', color: '#0f0f1a' },
});
