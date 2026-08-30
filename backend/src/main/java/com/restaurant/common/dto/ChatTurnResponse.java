package com.restaurant.common.dto;

import java.time.LocalDateTime;

/**
 * Response del chatbot al cliente.
 */
public record ChatTurnResponse(
    String turnId,
    String message,
    ActionPlan actionPlan,
    ConfirmationRequest confirmationRequest,
    OrderSnapshot orderSnapshot,
    LocalDateTime timestamp
) {}
