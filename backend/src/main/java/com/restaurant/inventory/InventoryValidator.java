package com.restaurant.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.dto.ActionPlan;
import org.springframework.stereotype.Component;

@Component
public class InventoryValidator {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public InventoryValidator(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    public void preValidateAction(ActionPlan.Action action) {
        if (action.type() == ActionPlan.ActionType.ADD_ITEM || action.type() == ActionPlan.ActionType.MODIFY_QUANTITY) {
            if (action.productId() != null && action.quantity() > 0) {
                inventoryService.validateAvailability(action.productId(), action.quantity(), modifiersJson(action));
            }
        }
    }

    private String modifiersJson(ActionPlan.Action action) {
        try {
            return action.modifiers() != null ? objectMapper.writeValueAsString(action.modifiers()) : "[]";
        } catch (Exception e) {
            return "[]";
        }
    }
}
