package com.restaurant.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Plan de acciones generado por el LLM, validado por el backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActionPlan(
    List<Action> actions,
    String responseMessage,
    double confidence,
    boolean requiresConfirmation,
    boolean clarificationNeeded,
    String clarificationMessage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(
        ActionType type,
        Long productId,
        String productName,
        int quantity,
        List<Modifier> modifiers,
        String reason
    ) {}

    public record Modifier(
        ModifierType type,
        Long ingredientId,
        String ingredientName
    ) {}

    public enum ActionType {
        ADD_ITEM,
        REMOVE_ITEM,
        MODIFY_QUANTITY,
        ADD_MODIFIER,
        REMOVE_MODIFIER,
        REQUEST_PAYMENT,
        CONFIRM_ORDER,
        CANCEL_ORDER
    }

    public enum ModifierType {
        REMOVE,
        ADD,
        SUBSTITUTE
    }
}
