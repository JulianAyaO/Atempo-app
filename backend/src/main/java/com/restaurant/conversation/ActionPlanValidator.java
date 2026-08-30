package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.common.dto.ActionPlan;
import com.restaurant.common.exception.InvalidActionPlanException;
import com.restaurant.inventory.InventoryValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ActionPlanValidator {

    private final CatalogService catalogService;
    private final InventoryValidator inventoryValidator;

    @Autowired
    public ActionPlanValidator(CatalogService catalogService, InventoryValidator inventoryValidator) {
        this.catalogService = catalogService;
        this.inventoryValidator = inventoryValidator;
    }

    ActionPlanValidator(CatalogService catalogService) {
        this(catalogService, null);
    }

    public void validate(ActionPlan plan) {
        if (plan == null) {
            throw new InvalidActionPlanException("El ActionPlan no puede ser nulo");
        }
        if (plan.actions() == null) {
            throw new InvalidActionPlanException("El ActionPlan debe tener una lista de acciones");
        }

        List<String> errors = new ArrayList<>();
        for (ActionPlan.Action action : plan.actions()) {
            try {
                validateAction(action);
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidActionPlanException("Errores de validación: " + String.join("; ", errors));
        }
    }

    public void validateSingleAction(ActionPlan.Action action) {
        validateAction(action);
    }

    private void validateAction(ActionPlan.Action action) {
        if (action.type() == null) {
            throw new InvalidActionPlanException("La acción debe tener un tipo");
        }

        switch (action.type()) {
            case ADD_ITEM, REMOVE_ITEM, MODIFY_QUANTITY, ADD_MODIFIER, REMOVE_MODIFIER -> {
                if (action.productId() == null) {
                    throw new InvalidActionPlanException("ADD_ITEM/REMOVE_ITEM/MODIFY_QUANTITY/MODIFIER requieren productId");
                }
                // Cantidad entre 1 y 100
                if (action.quantity() <= 0 || action.quantity() > 100) {
                    throw new InvalidActionPlanException("La cantidad debe estar entre 1 y 100");
                }

                var product = catalogService.getProductById(action.productId());
                if (product == null) {
                    throw new InvalidActionPlanException("Producto no encontrado: " + action.productId());
                }
                // Producto debe estar disponible (active = true)
                // Nota: para REMOVE_ITEM (quitar ingrediente) permitimos productos no disponibles
                // ya que el usuario podría querer quitar algo de un pedido previo
                if (action.type() == ActionPlan.ActionType.ADD_ITEM && !productIsAvailable(product.id())) {
                    throw new InvalidActionPlanException("El producto '" + product.name() + "' no está disponible");
                }
                if (inventoryValidator != null && (action.type() == ActionPlan.ActionType.ADD_ITEM || action.type() == ActionPlan.ActionType.MODIFY_QUANTITY)) {
                    inventoryValidator.preValidateAction(action);
                }
            }
            case CONFIRM_ORDER, REQUEST_PAYMENT, CANCEL_ORDER -> {
                // No requieren productId
            }
            default -> throw new InvalidActionPlanException("Tipo de acción no soportado: " + action.type());
        }
    }

    private boolean productIsAvailable(Long productId) {
        try {
            var p = catalogService.getProductEntity(productId);
            return p != null && p.isActive();
        } catch (Exception e) {
            return false;
        }
    }
}
