package com.restaurant.orders;

import com.restaurant.common.exception.InvalidActionPlanException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderStateMachine {

    private static final Map<String, List<String>> VALID_TRANSITIONS = Map.of(
        "DRAFT", List.of("PENDING", "CANCELLED"),
        "PENDING", List.of("IN_PREPARATION", "CANCELLED"),
        "IN_PREPARATION", List.of("READY", "CANCELLED"),
        "READY", List.of("DELIVERED"),
        "DELIVERED", List.of("PAYMENT_REQUESTED"),
        "PAYMENT_REQUESTED", List.of("PAID"),
        "PAID", List.of("CLOSED")
    );

    public boolean canTransition(String fromStatus, String toStatus) {
        List<String> allowed = VALID_TRANSITIONS.getOrDefault(fromStatus, List.of());
        return allowed.contains(toStatus);
    }

    public void validateTransition(String fromStatus, String toStatus) {
        if (!canTransition(fromStatus, toStatus)) {
            List<String> allowed = VALID_TRANSITIONS.getOrDefault(fromStatus, List.of());
            throw new InvalidActionPlanException(
                String.format("Transición inválida: %s → %s. Permitidas: %s",
                    fromStatus, toStatus, allowed));
        }
    }

    public List<String> getAllowedTransitions(String status) {
        return VALID_TRANSITIONS.getOrDefault(status, List.of());
    }
}
