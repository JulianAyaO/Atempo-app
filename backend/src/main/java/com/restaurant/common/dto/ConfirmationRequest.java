package com.restaurant.common.dto;

import java.util.List;

/**
 * Request de confirmación al usuario para operaciones sensibles.
 */
public record ConfirmationRequest(
    String turnId,
    String message,
    List<String> options,
    ActionPlan pendingActionPlan
) {}
