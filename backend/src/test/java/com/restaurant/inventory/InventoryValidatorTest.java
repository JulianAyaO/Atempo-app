package com.restaurant.inventory;

import com.restaurant.common.dto.ActionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class InventoryValidatorTest {

    @Test
    void preValidateAddItemCallsValidateAvailability() {
        InventoryService invService = mock(InventoryService.class);
        InventoryValidator validator = new InventoryValidator(invService, new ObjectMapper());

        ActionPlan.Action action = new ActionPlan.Action(
            ActionPlan.ActionType.ADD_ITEM, 1L, "Tacos", 2, null, null
        );

        assertDoesNotThrow(() -> validator.preValidateAction(action));
        verify(invService).validateAvailability(1L, 2, "[]");
    }
}
