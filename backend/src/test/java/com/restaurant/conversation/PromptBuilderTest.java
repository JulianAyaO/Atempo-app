package com.restaurant.conversation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    @Test
    void buildPromptIncludesCatalogContext() {
        PromptBuilder builder = new PromptBuilder();
        builder.init(); // carga template
        String prompt = builder.buildSystemPrompt("Tacos al Pastor", "Pedido vacío");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Tacos al Pastor"));
    }
}
