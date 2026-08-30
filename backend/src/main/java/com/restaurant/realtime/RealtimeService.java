package com.restaurant.realtime;

import com.restaurant.common.dto.OrderSnapshot;
import com.restaurant.orders.Order;
import com.restaurant.orders.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class RealtimeService {

    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectProvider<OrderService> orderService;
    private final Queue<Map<String, Object>> recentAlerts = new ConcurrentLinkedQueue<>();
    private static final int MAX_ALERTS = 100;

    public RealtimeService(SimpMessagingTemplate messagingTemplate, @Lazy ObjectProvider<OrderService> orderService) {
        this.messagingTemplate = messagingTemplate;
        this.orderService = orderService;
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return new ArrayList<>(recentAlerts);
    }

    public void dismissAlert(String type, Long tableId) {
        recentAlerts.removeIf(a ->
            type.equals(a.get("type")) &&
            tableId != null && tableId.equals(a.get("tableId"))
        );
    }

    /**
     * Notifica actualización de pedido a la mesa.
     */
    public void notifyOrderUpdate(Order order) {
        String dest = "/topic/table/" + order.getTableId() + "/orders";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ORDER_UPDATED");
        payload.put("orderId", order.getId());
        payload.put("status", order.getStatus());
        payload.put("total", order.getTotal());
        payload.put("timestamp", LocalDateTime.now().toString());
        try {
            payload.put("orderSnapshot", orderService.getObject().toSnapshot(order));
        } catch (Exception e) {
            log.debug("No se pudo adjuntar snapshot al ORDER_UPDATED", e);
        }
        messagingTemplate.convertAndSend(dest, payload);
        log.debug("WS → {} ORDER_UPDATED", dest);
    }

    /**
     * Notifica nuevo pedido a cocina.
     */
    public void notifyNewOrderToKitchen(Order order) {
        messagingTemplate.convertAndSend("/topic/kitchen/orders", Map.of(
            "type", "NEW_ORDER",
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "status", order.getStatus(),
            "itemCount", order.getItems().size(),
            "timestamp", LocalDateTime.now().toString()
        ));
        log.info("WS → cocina: NUEVO PEDIDO #{} Mesa {}", order.getId(), order.getTableId());
    }

    private void addAlert(Map<String, Object> alert) {
        recentAlerts.add(alert);
        while (recentAlerts.size() > MAX_ALERTS) {
            recentAlerts.poll();
        }
    }

    /**
     * Notifica que una mesa solicita un mesero.
     */
    public void notifyWaiterCall(Long tableId, String reason) {
        var alert = Map.<String, Object>of(
            "type", "WAITER_CALLED",
            "tableId", tableId,
            "message", "Mesa " + tableId + " solicita un mesero" + (reason != null ? ": " + reason : ""),
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/waiters/alerts", alert);
        addAlert(alert);
        log.info("WS → meseros: MESA {} LLAMO AL MESERO", tableId);
    }

    /**
     * Notifica pedido listo a meseros.
     */
    public void notifyOrderReady(Order order) {
        var alert = Map.<String, Object>of(
            "type", "ORDER_READY",
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "message", "Pedido #" + order.getId() + " listo para Mesa " + order.getTableId(),
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/waiters/alerts", alert);
        addAlert(alert);
        log.info("WS → meseros: PEDIDO LISTO #{} Mesa {}", order.getId(), order.getTableId());
    }

    /**
     * Notifica solicitud de cuenta a meseros.
     */
    public void notifyPaymentRequested(Order order) {
        var alert = Map.<String, Object>of(
            "type", "PAYMENT_REQUESTED",
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "total", order.getTotal(),
            "message", "Mesa " + order.getTableId() + " solicita la cuenta - $" + order.getTotal(),
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/waiters/alerts", alert);
        addAlert(alert);
        log.info("WS → meseros: CUENTA SOLICITADA Mesa {} Total ${}", order.getTableId(), order.getTotal());
    }

    /**
     * Notifica actualización del menú a todos los clientes.
     */
    public void notifyMenuUpdate() {
        messagingTemplate.convertAndSend("/topic/menu/updates", Map.of(
            "type", "MENU_UPDATED",
            "timestamp", LocalDateTime.now().toString()
        ));
        log.info("WS → todos: MENU_UPDATED");
    }

    /**
     * Envía mensaje de chat a la mesa.
     */
    public void sendChatMessage(Long tableId, Map<String, Object> message) {
        messagingTemplate.convertAndSend("/topic/table/" + tableId + "/chat", message);
    }

    /**
     * Notifica cambio de estado de pedido a cocina (para que el módulo mesero también lo reciba).
     */
    public void notifyOrderUpdateToKitchen(Order order) {
        messagingTemplate.convertAndSend("/topic/kitchen/orders", Map.of(
            "type", "ORDER_UPDATED",
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "status", order.getStatus(),
            "timestamp", LocalDateTime.now().toString()
        ));
        log.debug("WS → cocina: ESTADO ACTUALIZADO #{} → {}", order.getId(), order.getStatus());
    }

    /**
     * Notifica alerta de inventario bajo a admin y cocina.
     */
    public void notifyInventoryAlert(String ingredientName, double available, String unit) {
        var payload = Map.of(
            "type", "INVENTORY_LOW",
            "ingredient", ingredientName,
            "available", available,
            "unit", unit,
            "message", "⚠ Stock bajo: " + ingredientName + " (" + available + " " + unit + ")",
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/admin/events", payload);
        messagingTemplate.convertAndSend("/topic/kitchen/inventory", payload);
        log.info("WS → admin+cocina: STOCK BAJO {} ({} {})", ingredientName, available, unit);
    }

    /**
     * Notifica a la mesa que su sesión fue cerrada (servicio finalizado).
     */
    public void notifySessionClosed(Long tableId) {
        messagingTemplate.convertAndSend("/topic/table/" + tableId + "/orders", Map.of(
            "type", "SESSION_CLOSED",
            "tableId", tableId,
            "message", "Gracias por tu visita. El servicio ha finalizado.",
            "timestamp", LocalDateTime.now().toString()
        ));
        log.info("WS → mesa {}: SESION CERRADA", tableId);
    }

    /**
     * Notifica actualización general de inventario a admin y cocina.
     */
    public void notifyInventoryUpdated() {
        var payload = Map.of(
            "type", "INVENTORY_UPDATED",
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/admin/events", payload);
        messagingTemplate.convertAndSend("/topic/kitchen/inventory", payload);
        log.debug("WS → admin+cocina: INVENTORY_UPDATED");
    }

    /**
     * Notifica cambio de disponibilidad de producto a todos los clientes.
     */
    public void broadcastMenuUpdate(Long productId, String productName, boolean available) {
        var payload = Map.of(
            "type", "MENU_UPDATE",
            "productId", productId,
            "productName", productName,
            "available", available,
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/menu/updates", payload);
        messagingTemplate.convertAndSend("/topic/admin/events", payload);
        messagingTemplate.convertAndSend("/topic/kitchen/inventory", payload);
        log.info("WS → broadcast: Producto '{}' disponible={}", productName, available);
    }
}
