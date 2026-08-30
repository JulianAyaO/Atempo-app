package com.restaurant.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.audit.AuditService;
import com.restaurant.common.dto.*;
import com.restaurant.orders.Order;
import com.restaurant.orders.OrderService;
import com.restaurant.orders.Session;
import com.restaurant.realtime.RealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ConversationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ConversationOrchestrator.class);

    private final ConversationTurnRepository turnRepo;
    private final LLMService llmService;
    private final OrderService orderService;
    private final AuditService auditService;
    private final RealtimeService realtimeService;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ActionPlanValidator actionPlanValidator;
    private final ActionPlanExecutor actionPlanExecutor;
    private final ShortcutHandler shortcutHandler;
    private final CatalogContextBuilder catalogContextBuilder;
    private final ActionPlanEnricher actionPlanEnricher;

    @Value("${app.conversation.max-history-turns}")
    private int maxHistoryTurns;

    public ConversationOrchestrator(ConversationTurnRepository turnRepo,
                                    LLMService llmService,
                                    OrderService orderService,
                                    AuditService auditService,
                                    RealtimeService realtimeService,
                                    ObjectMapper objectMapper,
                                    PromptBuilder promptBuilder,
                                    ActionPlanValidator actionPlanValidator,
                                    ActionPlanExecutor actionPlanExecutor,
                                    ShortcutHandler shortcutHandler,
                                    CatalogContextBuilder catalogContextBuilder,
                                    ActionPlanEnricher actionPlanEnricher) {
        this.turnRepo = turnRepo;
        this.llmService = llmService;
        this.orderService = orderService;
        this.auditService = auditService;
        this.realtimeService = realtimeService;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.actionPlanValidator = actionPlanValidator;
        this.actionPlanExecutor = actionPlanExecutor;
        this.shortcutHandler = shortcutHandler;
        this.catalogContextBuilder = catalogContextBuilder;
        this.actionPlanEnricher = actionPlanEnricher;
    }

    @Transactional
    public ChatTurnResponse processMessage(ChatTurnRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = turnRepo.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Request duplicado: {}", request.idempotencyKey());
                return buildResponseFromTurn(existing.get(), request);
            }
        }

        Session session = orderService.resolveClientSession(request.sessionId(), request.tableId());
        String sessionId = session.getId();
        Order draftOrder = orderService.getOrCreateDraftOrder(sessionId, request.tableId());

        List<ConversationTurn> history = turnRepo.findRecentBySessionId(sessionId, maxHistoryTurns);
        Collections.reverse(history);

        ConversationTurn userTurn = new ConversationTurn();
        userTurn.setSessionId(sessionId);
        userTurn.setRole("USER");
        userTurn.setContent(request.message());
        userTurn.setIdempotencyKey(request.idempotencyKey());
        turnRepo.save(userTurn);

        String shortcutResponse = null;
        if (!"MENU".equals(request.source())) {
            shortcutResponse = shortcutHandler.handleShortcut(request.message(), draftOrder, history);
        }
        if (shortcutResponse != null) {
            OrderSnapshot snap = orderService.toSnapshot(orderService.getOrderById(draftOrder.getId()));
            ConversationTurn assistantTurn = saveAssistantTurn(sessionId, shortcutResponse, null, null, request.idempotencyKey());
            return new ChatTurnResponse(assistantTurn.getId(), shortcutResponse, null, null, snap, LocalDateTime.now());
        }

        ActionPlan emptyPlan = new ActionPlan(new ArrayList<>(), null, 1.0, false, false, null);
        OrderSnapshot draftSnapshot = orderService.toSnapshot(draftOrder);
        ActionPlan deterministic = actionPlanEnricher.enrichMissingActions(emptyPlan, request.message(), history, request.source(), draftSnapshot);
        deterministic = actionPlanEnricher.filterSpuriousActions(deterministic, request.message());
        boolean skipLlm = "MENU".equals(request.source()) || hasCartMutations(deterministic);
        if (skipLlm) {
            if (!hasCartMutations(deterministic)) {
                String fail = "No pude agregar ese producto al carrito. Inténtalo de nuevo desde el menú.";
                OrderSnapshot currentSnap = orderService.toSnapshot(orderService.getOrderById(draftOrder.getId()));
                ConversationTurn assistantTurn = saveAssistantTurn(sessionId, fail, null, null, request.idempotencyKey());
                return new ChatTurnResponse(assistantTurn.getId(), fail, null, null, currentSnap, LocalDateTime.now());
            }
            return executePlan(deterministic, draftOrder, sessionId, request, null);
        }
        if (deterministic.responseMessage() != null && !deterministic.responseMessage().isBlank()) {
            OrderSnapshot currentSnap = orderService.toSnapshot(orderService.getOrderById(draftOrder.getId()));
            ConversationTurn assistantTurn = saveAssistantTurn(sessionId, deterministic.responseMessage(), null, null, request.idempotencyKey());
            return new ChatTurnResponse(assistantTurn.getId(), deterministic.responseMessage(), deterministic, null, currentSnap, LocalDateTime.now());
        }

        String catalogContext = catalogContextBuilder.buildCatalogContext(buildContextQuery(request.message(), history));
        OrderSnapshot orderSnapshot = orderService.toSnapshot(draftOrder);
        String orderState = orderSnapshot.items().isEmpty() ? "Pedido vacío" : catalogContextBuilder.formatOrderState(orderSnapshot);

        String systemPrompt = promptBuilder.buildSystemPrompt(catalogContext, orderState);

        List<LLMService.ChatMessage> messages = new ArrayList<>();
        messages.add(new LLMService.ChatMessage("system", systemPrompt));
        for (ConversationTurn t : history) {
            if (!"SYSTEM".equals(t.getRole())) {
                messages.add(new LLMService.ChatMessage(t.getRole().toLowerCase(), t.getContent()));
            }
        }
        messages.add(new LLMService.ChatMessage("user", request.message()));

        String llmResponse;
        try {
            llmResponse = llmService.chat(messages);
        } catch (Exception e) {
            log.error("Error LLM", e);
            return createErrorResponse(sessionId, draftOrder, "Lo siento, hubo un error al procesar tu mensaje. ¿Podrías intentar de nuevo?");
        }

        ActionPlan actionPlan;
        try {
            JsonNode jsonNode = objectMapper.readTree(llmResponse);
            ObjectNode obj = (ObjectNode) jsonNode;
            if (!obj.has("actions")) obj.putArray("actions");
            if (!obj.has("responseMessage")) obj.put("responseMessage", "¿En que puedo ayudarte?");
            if (!obj.has("confidence")) obj.put("confidence", 1.0);
            if (!obj.has("requiresConfirmation")) obj.put("requiresConfirmation", false);
            if (!obj.has("clarificationNeeded")) obj.put("clarificationNeeded", false);
            if (!obj.has("clarificationMessage")) obj.putNull("clarificationMessage");
            actionPlan = objectMapper.treeToValue(obj, ActionPlan.class);
        } catch (Exception e) {
            log.warn("LLM devolvio texto libre (no JSON). Intentando extraer intenciones...");
            String llmMessage = extractResponseMessage(llmResponse);
            actionPlan = new ActionPlan(new ArrayList<>(), llmMessage, 0.5, false, false, null);
        }

        actionPlan = actionPlanEnricher.enrichMissingActions(actionPlan, request.message(), history, request.source(), orderService.toSnapshot(draftOrder));
        actionPlan = actionPlanEnricher.filterSpuriousActions(actionPlan, request.message());

        boolean hasOurActions = hasCartMutations(actionPlan);
        if (hasOurActions) {
            String msg = actionPlan.responseMessage();
            boolean isRejection = msg != null && (msg.toLowerCase().contains("no tenemos") || msg.toLowerCase().contains("no entiendo")
                || msg.toLowerCase().contains("no disponible") || msg.toLowerCase().contains("no es un producto")
                || msg.toLowerCase().contains("no tengo registro") || msg.toLowerCase().contains("no encontrado"));
            String newMsg = isRejection ? msg : actionPlanEnricher.buildConfirmationMessage(actionPlan.actions());
            actionPlan = new ActionPlan(actionPlan.actions(), newMsg, actionPlan.confidence(), false, false, null);
        }

        return executePlan(actionPlan, draftOrder, sessionId, request, llmResponse);
    }

    private boolean hasCartMutations(ActionPlan plan) {
        return plan != null && plan.actions() != null && plan.actions().stream()
            .anyMatch(a -> a.type() == ActionPlan.ActionType.ADD_ITEM
                || a.type() == ActionPlan.ActionType.REMOVE_ITEM
                || a.type() == ActionPlan.ActionType.MODIFY_QUANTITY
                || a.type() == ActionPlan.ActionType.ADD_MODIFIER
                || a.type() == ActionPlan.ActionType.REMOVE_MODIFIER);
    }

    private ChatTurnResponse executePlan(ActionPlan actionPlan, Order draftOrder, String sessionId,
                                         ChatTurnRequest request, String llmResponse) {
        ActionPlan validatedPlan = actionPlan;
        try {
            actionPlanValidator.validate(actionPlan);
        } catch (Exception e) {
            log.warn("ActionPlan parcialmente inválido: {}", e.getMessage());
            if (actionPlan.actions() != null && !actionPlan.actions().isEmpty()) {
                List<ActionPlan.Action> validActions = actionPlan.actions().stream()
                    .filter(a -> {
                        try { actionPlanValidator.validateSingleAction(a); return true; }
                        catch (Exception ex) { return false; }
                    }).toList();
                validatedPlan = new ActionPlan(validActions, actionPlan.responseMessage(),
                    actionPlan.confidence(), false, false, null);
            }
            if ((validatedPlan.actions() == null || validatedPlan.actions().isEmpty())
                    && actionPlan.responseMessage() != null && !actionPlan.responseMessage().isBlank()) {
                OrderSnapshot currentSnap = orderService.toSnapshot(orderService.getOrderById(draftOrder.getId()));
                ConversationTurn assistantTurn = saveAssistantTurn(sessionId, actionPlan.responseMessage(), llmResponse, null, request.idempotencyKey());
                return new ChatTurnResponse(assistantTurn.getId(), actionPlan.responseMessage(), null, null, currentSnap, LocalDateTime.now());
            }
        }

        if (hasCartMutations(validatedPlan)) {
            String confirm = actionPlanEnricher.buildConfirmationMessage(validatedPlan.actions());
            validatedPlan = new ActionPlan(validatedPlan.actions(), confirm, validatedPlan.confidence(), false, false, null);
        }

        if (validatedPlan.clarificationNeeded() && !hasCartMutations(validatedPlan)) {
            OrderSnapshot currentSnap = orderService.toSnapshot(orderService.getOrderById(draftOrder.getId()));
            ConversationTurn assistantTurn = saveAssistantTurn(sessionId, validatedPlan.responseMessage(), llmResponse, null, request.idempotencyKey());
            return new ChatTurnResponse(assistantTurn.getId(),
                validatedPlan.clarificationMessage() != null ? validatedPlan.clarificationMessage() : validatedPlan.responseMessage(),
                validatedPlan, null, currentSnap, LocalDateTime.now());
        }

        ApplyResult result = actionPlanExecutor.execute(validatedPlan, draftOrder);

        String finalMessage = validatedPlan.responseMessage();
        if (finalMessage == null || finalMessage.isBlank()) {
            finalMessage = actionPlanEnricher.buildConfirmationMessage(validatedPlan.actions());
        }
        if (!result.success() && !result.errors().isEmpty()) {
            StringBuilder errMsg = new StringBuilder(finalMessage != null ? finalMessage : "");
            errMsg.append("\n\nNota: ");
            for (String err : result.errors()) {
                errMsg.append(err).append(". ");
            }
            finalMessage = errMsg.toString().trim();
            log.warn("Errores de ejecución: {}", result.errors());
        }

        ConversationTurn assistantTurn = saveAssistantTurn(sessionId, finalMessage, llmResponse,
            objectMapper.valueToTree(result).toString(), request.idempotencyKey());

        auditService.logEvent("CHAT_TURN", "SESSION", sessionId,
            Map.of("message", request.message(), "actions", validatedPlan.actions() != null ? validatedPlan.actions().size() : 0),
            "table-" + request.tableId());

        realtimeService.sendChatMessage(request.tableId(), Map.of(
            "type", "ASSISTANT_MESSAGE",
            "message", finalMessage,
            "orderSnapshot", result.orderSnapshot(),
            "timestamp", LocalDateTime.now().toString()
        ));

        return new ChatTurnResponse(assistantTurn.getId(), finalMessage,
            validatedPlan, null, result.orderSnapshot(), LocalDateTime.now());
    }

    private String buildContextQuery(String currentMessage, List<ConversationTurn> history) {
        StringBuilder sb = new StringBuilder();
        if (history != null) {
            int start = Math.max(0, history.size() - 4);
            for (int i = start; i < history.size(); i++) {
                ConversationTurn turn = history.get(i);
                if (turn.getContent() != null && !turn.getContent().isBlank()) {
                    sb.append(turn.getContent()).append(' ');
                }
            }
        }
        if (currentMessage != null) sb.append(currentMessage);
        return sb.toString().trim();
    }

    private ConversationTurn saveAssistantTurn(String sessionId, String content, String plan, String result, String idempKey) {
        ConversationTurn t = new ConversationTurn();
        t.setSessionId(sessionId);
        t.setRole("ASSISTANT");
        t.setContent(content);
        if (plan != null && !plan.isBlank()) {
            try { objectMapper.readTree(plan); t.setActionPlan(plan); }
            catch (Exception e) { t.setActionPlan(null); }
        }
        t.setApplyResult(result);
        if (idempKey != null) t.setIdempotencyKey(idempKey + "-response");
        return turnRepo.save(t);
    }

    private ChatTurnResponse createErrorResponse(String sessionId, Order order, String msg) {
        saveAssistantTurn(sessionId, msg, null, null, null);
        return new ChatTurnResponse(null, msg, null, null, orderService.toSnapshot(order), LocalDateTime.now());
    }

    private String extractResponseMessage(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            for (String field : List.of("responseMessage", "response_message", "message", "response", "text", "reply")) {
                JsonNode msg = node.get(field);
                if (msg != null && !msg.isNull() && !msg.asText().isBlank()) return msg.asText();
            }
            if (node.isTextual()) return node.asText();
        } catch (Exception ignored) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("responseMessage[^\\w]*[\"']?([^\"',}]+)");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String extracted = m.group(1).trim();
                if (extracted.length() > 3) return extracted;
            }
            String cleaned = json.replaceAll("[{}\\[\\]\"]", "").trim();
            cleaned = cleaned.replaceAll("\\s*[,;:]\\s*", " ").trim();
            if (!cleaned.isBlank() && cleaned.length() < 500 && !cleaned.contains("actions")) return cleaned;
        }
        return null;
    }

    private ChatTurnResponse buildResponseFromTurn(ConversationTurn t, ChatTurnRequest req) {
        OrderSnapshot snap = null;
        try {
            Session s = orderService.getOrCreateSession(req.tableId());
            Order o = orderService.getOrCreateDraftOrder(s.getId(), req.tableId());
            snap = orderService.toSnapshot(o);
        } catch (Exception ignored) {}
        return new ChatTurnResponse(t.getId(), t.getContent(), null, null, snap, t.getCreatedAt());
    }
}
