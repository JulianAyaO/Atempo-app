package com.restaurant.realtime;

import com.restaurant.common.event.*;
import com.restaurant.orders.Order;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RealtimeEventListener {

    private final RealtimeService realtimeService;

    public RealtimeEventListener(RealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @Async
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        Order order = event.getOrder();
        realtimeService.notifyNewOrderToKitchen(order);
        realtimeService.notifyOrderUpdate(order);
    }

    @Async
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.getOrder();
        realtimeService.notifyOrderUpdate(order);
        realtimeService.notifyOrderUpdateToKitchen(order);
        if ("READY".equals(order.getStatus())) {
            realtimeService.notifyOrderReady(order);
        }
    }

    @Async
    @EventListener
    public void onInventoryLow(InventoryLowEvent event) {
        realtimeService.notifyInventoryAlert(
            event.getIngredientName(), event.getAvailable(), event.getUnit());
    }

    @Async
    @EventListener
    public void onPaymentRequested(PaymentRequestedEvent event) {
        realtimeService.notifyPaymentRequested(event.getOrder());
    }
}
