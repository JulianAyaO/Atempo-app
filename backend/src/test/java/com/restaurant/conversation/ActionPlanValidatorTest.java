package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.common.dto.ActionPlan;
import com.restaurant.common.exception.InvalidActionPlanException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ActionPlanValidatorTest {

    @Test
    void nullPlanThrows() {
        CatalogService catalog = mock(CatalogService.class);
        ActionPlanValidator validator = new ActionPlanValidator(catalog);
        assertThrows(InvalidActionPlanException.class, () -> validator.validate(null));
    }

    @Test
    void emptyActionsIsValid() {
        CatalogService catalog = mock(CatalogService.class);
        ActionPlanValidator validator = new ActionPlanValidator(catalog);
        ActionPlan plan = new ActionPlan(List.of(), "Hola", 1.0, false, false, null);
        validator.validate(plan);
    }
}
