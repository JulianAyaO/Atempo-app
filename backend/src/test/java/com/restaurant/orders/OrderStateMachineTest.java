package com.restaurant.orders;

import com.restaurant.common.exception.InvalidActionPlanException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void draftCanTransitionToPending() {
        assertTrue(stateMachine.canTransition("DRAFT", "PENDING"));
    }

    @Test
    void draftCannotTransitionToReady() {
        assertFalse(stateMachine.canTransition("DRAFT", "READY"));
    }

    @Test
    void validateInvalidTransitionThrows() {
        assertThrows(InvalidActionPlanException.class, () ->
            stateMachine.validateTransition("DRAFT", "READY"));
    }

    @Test
    void getAllowedTransitionsForDraft() {
        List<String> allowed = stateMachine.getAllowedTransitions("DRAFT");
        assertEquals(List.of("PENDING", "CANCELLED"), allowed);
    }
}
