package com.restaurant.common.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot del estado actual del pedido para mostrar al cliente.
 */
public record OrderSnapshot(
    Long orderId,
    String status,
    List<OrderItemSnapshot> items,
    BigDecimal subtotal,
    BigDecimal total,
    Long tableId,
    String sessionId
) {
    public record IngredientStatus(
        Long id,
        String name,
        String type,
        String status
    ) {}

    public record OrderItemSnapshot(
        Long itemId,
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        List<String> modifiers,
        String notes,
        List<IngredientStatus> ingredients
    ) {}
}
