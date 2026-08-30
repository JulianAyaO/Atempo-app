package com.restaurant.conversation;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private String systemPromptTemplate;

    @PostConstruct
    public void init() {
        try {
            var resource = new ClassPathResource("prompts/system_prompt.txt");
            systemPromptTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("No se pudo cargar system_prompt.txt", e);
            systemPromptTemplate = "Eres un asistente de restaurante. Responde en JSON.";
        }
    }

    public String buildSystemPrompt(String catalogContext, String orderState) {
        return systemPromptTemplate
            .replace("{catalog_context}", catalogContext != null ? catalogContext : "")
            .replace("{order_state}", orderState != null ? orderState : "Pedido vacío");
    }
}
