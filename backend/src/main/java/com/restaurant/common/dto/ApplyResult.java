package com.restaurant.common.dto;

import java.util.List;

/**
 * Resultado de aplicar un ActionPlan.
 */
public record ApplyResult(
    boolean success,
    OrderSnapshot orderSnapshot,
    List<String> errors,
    List<String> warnings
) {}
