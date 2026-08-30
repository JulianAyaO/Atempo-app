package com.restaurant.orders;

import com.restaurant.catalog.CatalogService;
import com.restaurant.catalog.Product;
import com.restaurant.common.dto.OrderSnapshot;
import com.restaurant.common.event.OrderCreatedEvent;
import com.restaurant.common.event.OrderStatusChangedEvent;
import com.restaurant.common.event.PaymentRequestedEvent;
import com.restaurant.common.exception.*;
import com.restaurant.inventory.InventoryService;
import com.restaurant.realtime.RealtimeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final SessionRepository sessionRepository;
    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final RealtimeService realtimeService;
    private final ObjectMapper objectMapper;
    private final OrderStateMachine orderStateMachine;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, SessionRepository sessionRepository,
                        CatalogService catalogService, InventoryService inventoryService,
                        RealtimeService realtimeService, ObjectMapper objectMapper,
                        OrderStateMachine orderStateMachine,
                        ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.sessionRepository = sessionRepository;
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.realtimeService = realtimeService;
        this.objectMapper = objectMapper;
        this.orderStateMachine = orderStateMachine;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Abre o reutiliza una sesión para una mesa.
     */
    @Transactional
    public Session getOrCreateSession(Long tableId) {
        Optional<Session> active = sessionRepository.findByTableIdAndStatus(tableId, "ACTIVE");
        if (active.isPresent()) {
            Session existing = active.get();
            // Si la sesión activa tiene un pedido PAID, cerrarla automáticamente
            List<Order> sessionOrders = orderRepository.findBySessionId(existing.getId());
            boolean hasPaidOrder = sessionOrders.stream().anyMatch(o -> "PAID".equals(o.getStatus()));
            if (hasPaidOrder) {
                existing.setStatus("CLOSED");
                existing.setClosedAt(LocalDateTime.now());
                sessionRepository.save(existing);
                log.info("Sesión {} cerrada automáticamente (tenía pedido PAID). Mesa {} liberada.", existing.getId(), tableId);
            } else {
                return existing;
            }
        }
        Session s = new Session();
        s.setTableId(tableId);
        return sessionRepository.save(s);
    }

    @Transactional
    public Session resolveClientSession(String requestedSessionId, Long tableId) {
        Session active = getOrCreateSession(tableId);
        if (requestedSessionId == null || requestedSessionId.isBlank()) {
            return active;
        }
        return sessionRepository.findById(requestedSessionId)
            .filter(s -> "ACTIVE".equals(s.getStatus()))
            .filter(s -> Objects.equals(s.getTableId(), tableId))
            .orElse(active);
    }

    /**
     * Obtiene o crea un pedido DRAFT para la sesión.
     */
    @Transactional
    public Order getOrCreateDraftOrder(String sessionId, Long tableId) {
        return orderRepository.findDraftBySessionId(sessionId)
            .orElseGet(() -> {
                Order o = new Order();
                o.setSessionId(sessionId);
                o.setTableId(tableId);
                o.setStatus("DRAFT");
                return orderRepository.save(o);
            });
    }

    /**
     * Agrega un item al pedido (transaccional con validación de inventario).
     */
    @Transactional
    public Order addItem(Long orderId, Long productId, int quantity, String modifiersJson) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + orderId));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        Product product = catalogService.getProductEntity(productId);

        // Solo valida disponibilidad. El stock se descuenta al confirmar el pedido.
        Optional<OrderItem> existing = order.getItems().stream()
            .filter(i -> i.getProductId().equals(productId) && "ACTIVE".equals(i.getStatus())
                && Objects.equals(i.getModifiers(), modifiersJson != null ? modifiersJson : "[]"))
            .findFirst();

        if (existing.isPresent()) {
            OrderItem item = existing.get();
            inventoryService.validateAvailability(productId, item.getQuantity() + quantity, item.getModifiers());
            item.setQuantity(item.getQuantity() + quantity);
            item.calculateLineTotal();
        } else {
            inventoryService.validateAvailability(productId, quantity, modifiersJson);
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(productId);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            item.setModifiers(modifiersJson != null ? modifiersJson : "[]");
            item.calculateLineTotal();
            order.getItems().add(item);
        }

        order.recalculateTotals();
        Order saved = orderRepository.save(order);

        // Notificar actualización
        realtimeService.notifyOrderUpdate(saved);
        return saved;
    }

    /**
     * Cancela (o reduce cantidad de) un item específico por itemId.
     * Esto permite diferenciar el mismo producto con modificadores distintos.
     */
    @Transactional
    public Order cancelItemById(Long orderId, Long itemId, int quantity) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        Optional<OrderItem> itemOpt = order.getItems().stream()
            .filter(i -> Objects.equals(i.getId(), itemId) && "ACTIVE".equals(i.getStatus()))
            .findFirst();

        if (itemOpt.isPresent()) {
            OrderItem item = itemOpt.get();
            if (quantity >= item.getQuantity()) {
                item.setStatus("CANCELLED");
            } else {
                item.setQuantity(item.getQuantity() - quantity);
                item.calculateLineTotal();
            }
        }

        order.recalculateTotals();
        Order saved = orderRepository.save(order);
        realtimeService.notifyOrderUpdate(saved);
        return saved;
    }

    @Transactional
    public Order cancelItemByIdForSession(Long orderId, Long itemId, int quantity, String sessionId) {
        assertOrderBelongsToSession(orderId, sessionId);
        return cancelItemById(orderId, itemId, quantity);
    }

    /**
     * Borra todos los productos del pedido borrador (sin cerrar la sesión).
     */
    @Transactional
    public Order clearDraftItems(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        for (OrderItem item : order.getItems()) {
            if ("ACTIVE".equals(item.getStatus())) {
                item.setStatus("CANCELLED");
            }
        }

        order.recalculateTotals();
        Order saved = orderRepository.save(order);
        realtimeService.notifyOrderUpdate(saved);
        return saved;
    }

    @Transactional
    public Order clearDraftItemsForSession(Long orderId, String sessionId) {
        assertOrderBelongsToSession(orderId, sessionId);
        return clearDraftItems(orderId);
    }

    /**
     * Remueve un item o reduce su cantidad.
     */
    @Transactional
    public Order removeItem(Long orderId, Long productId, int quantity) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        List<OrderItem> matches = order.getItems().stream()
            .filter(i -> i.getProductId().equals(productId) && "ACTIVE".equals(i.getStatus()))
            .toList();

        int remainingToRemove = Math.max(1, quantity);
        for (OrderItem item : matches) {
            if (remainingToRemove <= 0) break;
            if (remainingToRemove >= item.getQuantity()) {
                remainingToRemove -= item.getQuantity();
                item.setStatus("CANCELLED");
            } else {
                item.setQuantity(item.getQuantity() - remainingToRemove);
                item.calculateLineTotal();
                remainingToRemove = 0;
            }
        }

        order.recalculateTotals();
        Order saved = orderRepository.save(order);
        realtimeService.notifyOrderUpdate(saved);
        return saved;
    }

    /**
     * Modifica la cantidad de un item.
     */
    @Transactional
    public Order modifyItemQuantity(Long orderId, Long productId, int newQuantity) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        List<OrderItem> matches = order.getItems().stream()
            .filter(i -> i.getProductId().equals(productId) && "ACTIVE".equals(i.getStatus()))
            .toList();

        if (matches.size() > 1) {
            throw new InvalidActionPlanException("Hay varias líneas del mismo producto con modificadores distintos. Modifica el item específico desde el pedido.");
        }

        if (!matches.isEmpty()) {
            OrderItem item = matches.get(0);
            if (newQuantity <= 0) {
                item.setStatus("CANCELLED");
            } else {
                inventoryService.validateAvailability(productId, newQuantity, item.getModifiers());
                item.setQuantity(newQuantity);
                item.calculateLineTotal();
            }
        }

        order.recalculateTotals();
        Order saved = orderRepository.save(order);
        realtimeService.notifyOrderUpdate(saved);
        return saved;
    }

    @Transactional
    public Order mergeItemModifiers(Long orderId, Long productId, String incomingModifiersJson) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No se puede modificar un pedido en estado: " + order.getStatus());
        }

        List<OrderItem> matches = order.getItems().stream()
            .filter(i -> i.getProductId().equals(productId) && "ACTIVE".equals(i.getStatus()))
            .toList();
        if (matches.isEmpty()) {
            throw new InvalidActionPlanException("Ese producto no está en tu pedido actual");
        }
        OrderItem item = matches.get(matches.size() - 1);

        try {
            var product = catalogService.getProductById(productId);
            Set<Long> baseIds = new HashSet<>();
            Set<String> baseNames = new HashSet<>();
            if (product.ingredients() != null) {
                for (var ing : product.ingredients()) {
                    if ("BASE".equals(ing.type())) {
                        if (ing.id() != null) baseIds.add(ing.id());
                        if (ing.name() != null) baseNames.add(ing.name().toLowerCase(Locale.ROOT));
                    }
                }
            }
            List<Map<String, Object>> current = parseModifierList(item.getModifiers());
            List<Map<String, Object>> incoming = parseModifierList(incomingModifiersJson);
            for (Map<String, Object> inc : incoming) {
                Object incId = inc.get("ingredientId");
                String incName = String.valueOf(inc.getOrDefault("ingredientName", "")).toLowerCase(Locale.ROOT);
                boolean isRemove = "REMOVE".equalsIgnoreCase(String.valueOf(inc.getOrDefault("type", "")));
                Long incIdLong = incId instanceof Number n ? n.longValue() : null;
                if (isRemove && (baseIds.contains(incIdLong) || baseNames.contains(incName))) {
                    continue;
                }
                current.removeIf(existing -> {
                    Object existingId = existing.get("ingredientId");
                    String existingName = String.valueOf(existing.getOrDefault("ingredientName", "")).toLowerCase(Locale.ROOT);
                    boolean sameId = incId != null && existingId != null && String.valueOf(incId).equals(String.valueOf(existingId));
                    boolean sameName = !incName.isBlank() && incName.equals(existingName);
                    return sameId || sameName;
                });
                current.add(inc);
            }
            String merged = objectMapper.writeValueAsString(current);
            inventoryService.validateAvailability(productId, item.getQuantity(), merged);
            item.setModifiers(merged);
        } catch (InvalidActionPlanException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidActionPlanException("No se pudieron actualizar los ingredientes: " + e.getMessage());
        }

        order.recalculateTotals();
        Order savedMerged = orderRepository.save(order);
        realtimeService.notifyOrderUpdate(savedMerged);
        return savedMerged;
    }

    private List<Map<String, Object>> parseModifierList(String json) throws Exception {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }
        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(json);
        if (node.isTextual()) {
            node = objectMapper.readTree(node.asText());
        }
        if (node == null || !node.isArray()) {
            return new ArrayList<>();
        }
        return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * Confirma un pedido (DRAFT → PENDING), descuenta inventario.
     */
    @Transactional
    public Order confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        if (!"DRAFT".equals(order.getStatus())) {
            throw new InvalidActionPlanException("Solo se puede confirmar un pedido en estado DRAFT");
        }

        List<OrderItem> activeItems = order.getItems().stream()
            .filter(i -> "ACTIVE".equals(i.getStatus())).toList();

        if (activeItems.isEmpty()) {
            throw new InvalidActionPlanException("El pedido no tiene items activos");
        }

        // Validar y descontar inventario para cada item
        for (OrderItem item : activeItems) {
            inventoryService.validateAvailability(item.getProductId(), item.getQuantity(), item.getModifiers());
            inventoryService.deductStock(item.getProductId(), item.getQuantity(), "ORDER-" + orderId, item.getModifiers());
        }

        orderStateMachine.validateTransition(order.getStatus(), "PENDING");
        order.setStatus("PENDING");
        order.recalculateTotals();
        Order saved = orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreatedEvent(this, saved));

        log.info("Pedido {} confirmado. Mesa {}. Total: ${}", orderId, order.getTableId(), order.getTotal());
        return saved;
    }

    @Transactional
    public Order confirmOrderForSession(Long orderId, String sessionId) {
        assertOrderBelongsToSession(orderId, sessionId);
        return confirmOrder(orderId);
    }

    /**
     * Cambia el estado de un pedido (con validación de máquina de estados).
     */
    @Transactional
    public Order changeStatus(Long orderId, String newStatus) {
        try {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

            String previousStatus = order.getStatus();
            orderStateMachine.validateTransition(previousStatus, newStatus);

            // Si se cancela, revertir stock consumido
            if ("CANCELLED".equals(newStatus)) {
                for (OrderItem item : order.getItems()) {
                    if ("ACTIVE".equals(item.getStatus())) {
                        inventoryService.revertStockForOrder(item.getProductId(), item.getQuantity(), "CANCEL-" + orderId, item.getModifiers());
                        item.setStatus("CANCELLED");
                    }
                }
            }

            order.setStatus(newStatus);
            Order saved = orderRepository.save(order);
            eventPublisher.publishEvent(new OrderStatusChangedEvent(this, saved, previousStatus));

            // Si se marca como PAID, cerrar sesión y liberar mesa
            if ("PAID".equals(newStatus)) {
                Session session = sessionRepository.findById(order.getSessionId()).orElse(null);
                if (session != null && !"CLOSED".equals(session.getStatus())) {
                    session.setStatus("CLOSED");
                    session.setClosedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                    realtimeService.notifySessionClosed(order.getTableId());
                    log.info("Sesión {} cerrada. Mesa {} liberada.", session.getId(), order.getTableId());
                }
                realtimeService.dismissAlert("PAYMENT_REQUESTED", order.getTableId());
            }

            if ("DELIVERED".equals(newStatus)) {
                realtimeService.dismissAlert("ORDER_READY", order.getTableId());
            }

            if ("PAYMENT_REQUESTED".equals(newStatus)) {
                eventPublisher.publishEvent(new PaymentRequestedEvent(this, saved));
            }

            log.info("Pedido {} → estado: {}", orderId, newStatus);
            return saved;
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException("El pedido fue modificado por otro usuario. Intenta de nuevo.");
        }
    }

    /**
     * Solicitar la cuenta desde el chat.
     */
    @Transactional
    public Order requestPayment(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + orderId));
        if (!"DELIVERED".equals(order.getStatus())) {
            throw new InvalidActionPlanException("No puedes solicitar la cuenta hasta que el pedido haya sido entregado. Estado actual: " + order.getStatus());
        }
        return changeStatus(orderId, "PAYMENT_REQUESTED");
    }

    /**
     * Cliente llama al mesero directamente (botón).
     */
    @Transactional
    public void callWaiter(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Sesión no encontrada: " + sessionId));
        realtimeService.notifyWaiterCall(session.getTableId(), "Cliente tiene dudas");
    }

    /**
     * Cliente solicita la cuenta directamente (botón) — busca el pedido activo.
     */
    @Transactional
    public Order requestPaymentBySession(String sessionId, Long tableId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Sesión no encontrada: " + sessionId));
        if (tableId != null && !tableId.equals(session.getTableId())) {
            throw new AccessDeniedException("La mesa no coincide con la sesión indicada");
        }
        List<Order> orders = orderRepository.findBySessionId(sessionId);
        Order activeOrder = orders.stream()
            .filter(o -> List.of("PENDING", "IN_PREPARATION", "READY", "DELIVERED").contains(o.getStatus()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No hay un pedido activo para solicitar la cuenta. Debes confirmar tu pedido primero."));
        if (!"DELIVERED".equals(activeOrder.getStatus())) {
            throw new InvalidActionPlanException("No puedes solicitar la cuenta hasta que el pedido haya sido entregado. Estado actual: " + activeOrder.getStatus());
        }
        return changeStatus(activeOrder.getId(), "PAYMENT_REQUESTED");
    }

    /**
     * Obtiene todos los pedidos activos (para cocina).
     */
    public List<Order> getActiveOrders() {
        return orderRepository.findActiveOrders();
    }

    /**
     * Obtiene todos los pedidos que pidieron cuenta (para meseros).
     */
    public List<Order> getPaymentRequestedOrders() {
        return orderRepository.findPaymentRequestedOrders();
    }

    /**
     * Obtiene el pedido activo más reciente de una mesa (no-CLOSED/CANCELLED).
     */
    public List<Order> getActiveOrdersByTable(Long tableId) {
        return orderRepository.findActiveOrdersByTableId(tableId);
    }

    /**
     * Obtiene el pedido activo de la sesión activa de la mesa.
     * Si la sesión tiene un pedido PAID, la sesión se cierra y se retorna null.
     */
    @Transactional
    public Order getCurrentOrderForTable(Long tableId) {
        Session session = getOrCreateSession(tableId);
        return orderRepository.findBySessionId(session.getId()).stream()
            .filter(o -> !List.of("CLOSED", "CANCELLED", "PAID").contains(o.getStatus()))
            .max(Comparator.comparing(Order::getCreatedAt))
            .orElse(null);
    }

    /**
     * Convierte un Order a OrderSnapshot para la respuesta del chat.
     */
    public OrderSnapshot toSnapshot(Order order) {
        List<OrderSnapshot.OrderItemSnapshot> items = order.getItems().stream()
            .filter(i -> "ACTIVE".equals(i.getStatus()))
            .map(i -> {
                CatalogService.ProductDTO product = catalogService.getProductById(i.getProductId());
                List<String> mods = formatModifierLabels(i.getModifiers());
                List<OrderSnapshot.IngredientStatus> ingredients = buildIngredientStatuses(product, i.getModifiers());
                return new OrderSnapshot.OrderItemSnapshot(
                    i.getId(), i.getProductId(), product.name(), i.getQuantity(),
                    i.getUnitPrice(), i.getLineTotal(), mods, i.getNotes(), ingredients
                );
            })
            .collect(Collectors.toList());

        return new OrderSnapshot(order.getId(), order.getStatus(), items, order.getSubtotal(), order.getTotal(), order.getTableId(), order.getSessionId());
    }

    private List<String> formatModifierLabels(String raw) {
        List<String> mods = new ArrayList<>();
        try {
            for (Map<String, Object> mod : parseModifierList(raw)) {
                Object typeObj = mod.get("type");
                Object nameObj = mod.get("ingredientName");
                if (nameObj == null) nameObj = mod.get("name");
                String type = typeObj != null ? String.valueOf(typeObj) : "";
                String ingredientName = nameObj != null ? String.valueOf(nameObj) : "";
                if (ingredientName.isBlank()) continue;
                if ("REMOVE".equalsIgnoreCase(type)) mods.add("Sin " + ingredientName);
                else if ("ADD".equalsIgnoreCase(type)) mods.add("Con " + ingredientName);
                else if ("SUBSTITUTE".equalsIgnoreCase(type)) mods.add("Sustituir " + ingredientName);
                else mods.add(ingredientName);
            }
        } catch (Exception e) {
            log.warn("No se pudieron leer modificadores del pedido: {}", raw);
        }
        return mods;
    }

    private List<OrderSnapshot.IngredientStatus> buildIngredientStatuses(CatalogService.ProductDTO product, String rawModifiers) {
        List<OrderSnapshot.IngredientStatus> rows = new ArrayList<>();
        if (product.ingredients() == null) return rows;
        Set<String> removed = new HashSet<>();
        Set<String> added = new HashSet<>();
        for (String label : formatModifierLabels(rawModifiers)) {
            String lower = label.toLowerCase(Locale.ROOT);
            if (lower.startsWith("sin ")) removed.add(lower.substring(4).trim());
            else if (lower.startsWith("con ")) added.add(lower.substring(4).trim());
        }
        for (var ing : product.ingredients()) {
            String name = ing.name() == null ? "" : ing.name();
            String key = name.toLowerCase(Locale.ROOT);
            String type = ing.type() == null ? "BASE" : ing.type();
            String status = "included";
            boolean isRemoved = removed.stream().anyMatch(r -> key.equals(r) || key.contains(r) || r.contains(key));
            boolean isAdded = added.stream().anyMatch(a -> key.equals(a) || key.contains(a) || a.contains(key));
            if ("BASE".equals(type)) {
                status = "included";
            } else if (isRemoved && "REMOVABLE".equals(type)) {
                status = "removed";
            } else if ("OPTIONAL".equals(type)) {
                status = isAdded ? "added" : "available";
            }
            rows.add(new OrderSnapshot.IngredientStatus(ing.id(), name, type, status));
        }
        return rows;
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + id));
    }

    public List<Order> getOrdersBySession(String sessionId) {
        return orderRepository.findBySessionId(sessionId);
    }

    /**
     * Cierra la sesión y todos sus pedidos. Notifica al cliente.
     */
    @Transactional
    public void closeSession(String sessionId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException("Sesión no encontrada: " + sessionId));
        if ("CLOSED".equals(session.getStatus())) {
            return; // ya cerrada, evitar duplicar notificación
        }
        session.setStatus("CLOSED");
        session.setClosedAt(LocalDateTime.now());
        sessionRepository.save(session);
        realtimeService.notifySessionClosed(session.getTableId());
        realtimeService.dismissAlert("ORDER_READY", session.getTableId());
        realtimeService.dismissAlert("PAYMENT_REQUESTED", session.getTableId());
        realtimeService.dismissAlert("WAITER_CALLED", session.getTableId());
    }

    /**
     * Cancela todos los pedidos DRAFT de una mesa (para limpiar al entrar al chat).
     */
    @Transactional
    public void resetDraftForTable(Long tableId) {
        List<Order> drafts = orderRepository.findDraftsByTableId(tableId);
        for (Order o : drafts) {
            o.setStatus("CANCELLED");
            o.getItems().forEach(i -> i.setStatus("CANCELLED"));
            orderRepository.save(o);
        }
        // También cerrar sesión anterior para crear una limpia
        sessionRepository.findByTableIdAndStatus(tableId, "ACTIVE")
            .ifPresent(s -> {
                s.setStatus("CLOSED");
                s.setClosedAt(LocalDateTime.now());
                sessionRepository.save(s);
            });
        log.info("Reset drafts para mesa {}: {} pedidos cancelados", tableId, drafts.size());
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return realtimeService.getRecentAlerts();
    }

    public void dismissAlert(String type, Long tableId) {
        realtimeService.dismissAlert(type, tableId);
    }

    private void assertOrderBelongsToSession(Long orderId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new AccessDeniedException("sessionId requerido");
        }
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Pedido no encontrado: " + orderId));
        if (!sessionId.equals(order.getSessionId())) {
            throw new AccessDeniedException("El pedido no pertenece a la sesión indicada");
        }
    }
}
