package com.restaurant.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Servicio de LLM — implementación con Ollama (API nativa).
 * Usa /api/chat con format="json" para garantizar respuestas JSON válidas.
 */
@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.chat-model}")
    private String model;

    @Value("${app.openai.base-url}")
    private String baseUrl;

    @Value("${app.openai.max-tokens}")
    private int maxTokens;

    @Value("${app.openai.temperature}")
    private double temperature;

    public LLMService(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public record ChatMessage(String role, String content) {}

    /**
     * Envía una conversación al LLM y obtiene la respuesta.
     * Usa la API nativa de Ollama con format="json" para garantizar JSON válido.
     */
    public String chat(List<ChatMessage> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            // NOTA: format="json" omitido para modelos pequenos (3B) que generan
            // JSON inconsistente. Mejor dejar que generen texto libre y extraer
            // JSON manualmente con extractJson().
            ObjectNode options = requestBody.putObject("options");
            options.put("num_predict", maxTokens);
            options.put("temperature", temperature);

            ArrayNode messagesArray = requestBody.putArray("messages");
            for (ChatMessage msg : messages) {
                ObjectNode msgNode = messagesArray.addObject();
                msgNode.put("role", msg.role());
                msgNode.put("content", msg.content());
            }

            log.info("LLM request: model={}, messages={}", model, messages.size());

            Request request = new Request.Builder()
                .url(baseUrl + "/api/chat")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(objectMapper.writeValueAsString(requestBody), JSON_MEDIA))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "sin detalle";
                    log.error("Error Ollama: {} - {}", response.code(), errorBody);
                    throw new RuntimeException("Error del servicio LLM: " + response.code());
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                String content = root.path("message").path("content").asText();
                log.info("LLM response: {}", content);
                return extractJson(content);
            }
        } catch (IOException e) {
            log.error("Error comunicándose con Ollama", e);
            throw new RuntimeException("Error de comunicación con el servicio de IA: " + e.getMessage());
        }
    }

    /**
     * Extrae JSON puro del texto de respuesta.
     */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) return text;
        String trimmed = text.trim();

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed;
        }

        int firstBrace = trimmed.indexOf("{");
        int lastBrace = trimmed.lastIndexOf("}");
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }
}
