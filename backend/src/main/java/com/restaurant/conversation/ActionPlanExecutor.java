package com.restaurant.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.common.dto.ActionPlan;
import com.restaurant.common.dto.ApplyResult;
import com.restaurant.common.dto.OrderSnapshot;
import com.restaurant.common.exception.InsufficientInventoryException;
import com.restaurant.common.exception.InvalidActionPlanException;
import com.restaurant.orders.Order;
import com.restaurant.orders.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActionPlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanExecutor.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ActionPlanExecutor(OrderService orderService, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ApplyResult execute(ActionPlan plan, Order order) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            for (ActionPlan.Action action : plan.actions()) {
                try {
                    executeSingleAction(action, order);
                } catch (InsufficientInventoryException | InvalidActionPlanException e) {
                    errors.add(e.getMessage());
                    status.setRollbackOnly();
                    return;
                } catch (Exception e) {
                    errors.add("Error ejecutando acción " + action.type() + ": " + e.getMessage());
                    log.error("Error ejecutando acción", e);
                    status.setRollbackOnly();
                    return;
                }
            }
        });

        Order updated = orderService.getOrderById(order.getId());
        OrderSnapshot snapshot = orderService.toSnapshot(updated);
        return new ApplyResult(errors.isEmpty(), snapshot, errors, warnings);
    }

    private void executeSingleAction(ActionPlan.Action action, Order order) throws Exception {
        switch (action.type()) {
            case ADD_ITEM -> orderService.addItem(order.getId(), action.productId(), action.quantity(),
                action.modifiers() != null ? objectMapper.writeValueAsString(action.modifiers()) : "[]");
            case REMOVE_ITEM -> orderService.removeItem(order.getId(), action.productId(), action.quantity());
            case MODIFY_QUANTITY -> orderService.modifyItemQuantity(order.getId(), action.productId(), action.quantity());
            case ADD_MODIFIER, REMOVE_MODIFIER -> orderService.mergeItemModifiers(order.getId(), action.productId(),
                action.modifiers() != null ? objectMapper.writeValueAsString(action.modifiers()) : "[]");
            case CONFIRM_ORDER -> orderService.confirmOrder(order.getId());
            case CANCEL_ORDER -> orderService.changeStatus(order.getId(), "CANCELLED");
            case REQUEST_PAYMENT -> orderService.requestPayment(order.getId());
            default -> log.warn("Acción no implementada: {}", action.type());
        }
    }
}
