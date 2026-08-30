package com.restaurant.conversation;

import com.restaurant.common.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/turn")
    public ResponseEntity<ChatTurnResponse> processTurn(@Valid @RequestBody ChatTurnRequest request) {
        ChatTurnResponse response = conversationService.processMessage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ConversationTurn>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(conversationService.getHistory(sessionId));
    }

    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, String>> clearHistory(@PathVariable String sessionId) {
        conversationService.clearHistory(sessionId);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Historial de chat borrado"));
    }
}
