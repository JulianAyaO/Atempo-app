package com.restaurant.audit;

import com.restaurant.common.event.OrderCreatedEvent;
import com.restaurant.common.event.OrderStatusChangedEvent;
import com.restaurant.common.event.PaymentRequestedEvent;
import com.restaurant.orders.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderOutboxEventListener {

    private final OutboxService outboxService;

    public OrderOutboxEventListener(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        outboxService.emit("ORDER_CREATED", "ORDER", String.valueOf(order.getId()), orderPayload(order));
    }

    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.getOrder();
        outboxService.emit("ORDER_STATUS_CHANGED", "ORDER", String.valueOf(order.getId()), Map.of(
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "sessionId", order.getSessionId(),
            "previousStatus", event.getPreviousStatus(),
            "status", order.getStatus(),
            "total", order.getTotal()
        ));
    }

    @EventListener
    public void onPaymentRequested(PaymentRequestedEvent event) {
        Order order = event.getOrder();
        outboxService.emit("PAYMENT_REQUESTED", "ORDER", String.valueOf(order.getId()), orderPayload(order));
    }

    private Map<String, Object> orderPayload(Order order) {
        return Map.of(
            "orderId", order.getId(),
            "tableId", order.getTableId(),
            "sessionId", order.getSessionId(),
            "status", order.getStatus(),
            "total", order.getTotal()
        );
    }
}
