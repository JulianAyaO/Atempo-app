package com.restaurant.common.event;

import org.springframework.context.ApplicationEvent;

public class InventoryLowEvent extends ApplicationEvent {
    private final String ingredientName;
    private final double available;
    private final String unit;

    public InventoryLowEvent(Object source, String ingredientName, double available, String unit) {
        super(source);
        this.ingredientName = ingredientName;
        this.available = available;
        this.unit = unit;
    }

    public String getIngredientName() { return ingredientName; }
    public double getAvailable() { return available; }
    public String getUnit() { return unit; }
}
