package com.restaurant.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request del cliente al chatbot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatTurnRequest(
    String sessionId,
    @NotNull(message = "tableId es obligatorio")
    Long tableId,
    @NotBlank(message = "message es obligatorio")
    String message,
    String audioBase64,
    String idempotencyKey,
    String source
) {
    public ChatTurnRequest(String sessionId, Long tableId, String message, String audioBase64, String idempotencyKey) {
        this(sessionId, tableId, message, audioBase64, idempotencyKey, null);
    }
}
