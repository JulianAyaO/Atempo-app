package com.restaurant.orders;

import com.restaurant.common.dto.OrderSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<OrderSnapshot> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Long tableId = request.tableId();
        Session session = orderService.getOrCreateSession(tableId);
        Order draft = orderService.getOrCreateDraftOrder(session.getId(), tableId);
        return ResponseEntity.ok(orderService.toSnapshot(draft));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('KITCHEN','WAITER','ADMIN')")
    public ResponseEntity<List<OrderSnapshot>> getActiveOrders() {
        List<OrderSnapshot> snapshots = orderService.getActiveOrders().stream()
            .map(orderService::toSnapshot)
            .collect(Collectors.toList());
        return ResponseEntity.ok(snapshots);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('KITCHEN','WAITER','ADMIN')")
    public ResponseEntity<OrderSnapshot> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.toSnapshot(orderService.getOrderById(id)));
    }

    @GetMapping("/table/{tableId}")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<List<OrderSnapshot>> getOrdersByTable(@PathVariable Long tableId) {
        // Buscar sesión activa para la mesa
        Session session = orderService.getOrCreateSession(tableId);
        List<OrderSnapshot> snapshots = orderService.getOrdersBySession(session.getId()).stream()
            .map(orderService::toSnapshot)
            .collect(Collectors.toList());
        return ResponseEntity.ok(snapshots);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('KITCHEN','WAITER','ADMIN')")
    public ResponseEntity<OrderSnapshot> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest body) {
        Order order = orderService.changeStatus(id, body.status());
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<OrderSnapshot> confirmOrder(@PathVariable Long id, @Valid @RequestBody ClientOrderRequest body) {
        Order order = orderService.confirmOrderForSession(id, body.sessionId());
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    @PostMapping("/{id}/clear")
    public ResponseEntity<OrderSnapshot> clearDraftItems(@PathVariable Long id, @Valid @RequestBody ClientOrderRequest body) {
        Order order = orderService.clearDraftItemsForSession(id, body.sessionId());
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    @PostMapping("/{id}/items/{itemId}/cancel")
    public ResponseEntity<OrderSnapshot> cancelItemById(
        @PathVariable Long id,
        @PathVariable Long itemId,
        @Valid @RequestBody CancelItemRequest body
    ) {
        Order order = orderService.cancelItemByIdForSession(id, itemId, body.quantity(), body.sessionId());
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    @PostMapping("/{id}/request-payment")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<OrderSnapshot> requestPayment(@PathVariable Long id) {
        Order order = orderService.requestPayment(id);
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    @GetMapping("/payment-requested")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<List<OrderSnapshot>> getPaymentRequested() {
        List<OrderSnapshot> snapshots = orderService.getPaymentRequestedOrders().stream()
            .map(orderService::toSnapshot)
            .collect(Collectors.toList());
        return ResponseEntity.ok(snapshots);
    }

    // Endpoint para crear/obtener sesión de mesa
    @PostMapping("/sessions/table/{tableId}")
    public ResponseEntity<Map<String, Object>> getOrCreateSession(@PathVariable Long tableId) {
        Session session = orderService.getOrCreateSession(tableId);
        return ResponseEntity.ok(Map.of(
            "sessionId", session.getId(),
            "tableId", session.getTableId(),
            "status", session.getStatus()
        ));
    }

    // Obtener pedido borrador actual de una mesa (sin resetear)
    @GetMapping("/table/{tableId}/draft")
    public ResponseEntity<OrderSnapshot> getTableDraft(@PathVariable Long tableId) {
        Session session = orderService.getOrCreateSession(tableId);
        Order draft = orderService.getOrCreateDraftOrder(session.getId(), tableId);
        return ResponseEntity.ok(orderService.toSnapshot(draft));
    }

    // Obtener el pedido activo actual de la mesa (solo del session activa)
    @GetMapping("/table/{tableId}/current")
    public ResponseEntity<OrderSnapshot> getCurrentTableOrder(@PathVariable Long tableId) {
        Order order = orderService.getCurrentOrderForTable(tableId);
        if (order == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    // Resetear pedido borrador al entrar al chat (solo si el cliente lo solicita explícitamente)
    @PostMapping("/table/{tableId}/reset-draft")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<Map<String, String>> resetDraft(@PathVariable Long tableId) {
        orderService.resetDraftForTable(tableId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    // Cerrar sesión de mesa (finalizar servicio - mesero)
    @PostMapping("/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<Map<String, String>> closeSession(@PathVariable String sessionId) {
        orderService.closeSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Servicio finalizado"));
    }

    // Cliente llama al mesero directamente (botón)
    @PostMapping("/sessions/{sessionId}/call-waiter")
    public ResponseEntity<Map<String, String>> callWaiter(@PathVariable String sessionId) {
        orderService.callWaiter(sessionId);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Mesero notificado"));
    }

    // Cliente solicita la cuenta directamente (botón)
    @PostMapping("/sessions/{sessionId}/request-payment")
    public ResponseEntity<OrderSnapshot> requestPaymentBySession(@PathVariable String sessionId, @Valid @RequestBody PaymentSessionRequest body) {
        Order order = orderService.requestPaymentBySession(sessionId, body.tableId());
        return ResponseEntity.ok(orderService.toSnapshot(order));
    }

    // Fallback HTTP polling para alertas de meseros (WebSocket no siempre funciona en móviles)
    @GetMapping("/alerts/recent")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getRecentAlerts() {
        return ResponseEntity.ok(orderService.getRecentAlerts());
    }

    // Marcar alerta como atendida (eliminar del buffer)
    @PostMapping("/alerts/dismiss")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public ResponseEntity<Map<String, String>> dismissAlert(@Valid @RequestBody AlertDismissRequest body) {
        orderService.dismissAlert(body.type(), body.tableId());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    public record CreateOrderRequest(@NotNull Long tableId) {}
    public record StatusChangeRequest(@NotBlank String status) {}
    public record ClientOrderRequest(@NotBlank String sessionId) {}
    public record CancelItemRequest(@NotNull @Positive Integer quantity, @NotBlank String sessionId) {}
    public record PaymentSessionRequest(@NotNull Long tableId) {}
    public record AlertDismissRequest(@NotBlank String type, @NotNull Long tableId) {}
}
