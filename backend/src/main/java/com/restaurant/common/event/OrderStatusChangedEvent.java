package com.restaurant.common.event;

import com.restaurant.orders.Order;
import org.springframework.context.ApplicationEvent;

public class OrderStatusChangedEvent extends ApplicationEvent {
    private final Order order;
    private final String previousStatus;

    public OrderStatusChangedEvent(Object source, Order order, String previousStatus) {
        super(source);
        this.order = order;
        this.previousStatus = previousStatus;
    }

    public Order getOrder() { return order; }
    public String getPreviousStatus() { return previousStatus; }
}
