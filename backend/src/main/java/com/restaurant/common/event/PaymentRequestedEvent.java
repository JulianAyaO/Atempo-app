package com.restaurant.common.event;

import com.restaurant.orders.Order;
import org.springframework.context.ApplicationEvent;

public class PaymentRequestedEvent extends ApplicationEvent {
    private final Order order;

    public PaymentRequestedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() { return order; }
}
