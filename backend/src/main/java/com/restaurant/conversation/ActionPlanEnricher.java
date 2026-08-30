package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.common.dto.ActionPlan;
import com.restaurant.common.dto.OrderSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ActionPlanEnricher {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanEnricher.class);

    private final CatalogService catalogService;

    private static final Set<String> GENERIC_FOOD = Set.of(
        "tacos", "taco", "burrito", "burritos", "nachos", "sopes", "sopa", "ensalada", "postre"
    );
    private static final Set<String> NAME_STOPWORDS = Set.of(
        "de", "del", "con", "al", "la", "el", "los", "las", "en", "un", "una", "y", "a", "para", "por"
    );
    private static final Map<String, List<String>> PRODUCT_SYNONYMS;

    static {
        Map<String, List<String>> map = new HashMap<>();
        map.put("tacos al pastor", List.of("pastor", "carne dulce", "trompo", "adobada"));
        map.put("tacos de bistec", List.of("bistec", "res", "carne asada"));
        map.put("tacos de pollo", List.of("pollo", "chicken", "pollo a la plancha"));
        map.put("tacos de camaron", List.of("camaron", "camarones", "shrimp"));
        map.put("tacos gobernador", List.of("gobernador", "camarones con queso"));
        map.put("quesadilla de queso oaxaca", List.of("quesadilla", "quesadilla de queso", "queso"));
        map.put("enchiladas verdes", List.of("enchiladas verdes", "verdes"));
        map.put("enchiladas de mole", List.of("enchiladas de mole", "mole", "enchiladas negras"));
        map.put("burrito de carne asada", List.of("burrito", "burro"));
        map.put("chile relleno de queso", List.of("chile relleno", "relleno"));
        map.put("mole poblano con pollo", List.of("mole poblano", "pollo con mole"));
        map.put("pescado a la veracruzana", List.of("pescado", "veracruzana", "filete"));
        map.put("ensalada cesar con pollo", List.of("cesar", "cesar con pollo", "salada cesar"));
        map.put("flan napolitano", List.of("flan", "flan de vainilla"));
        map.put("churros con chocolate", List.of("churros", "chocolate", "churritos"));
        map.put("pastel de tres leches", List.of("tres leches", "pastel"));
        map.put("agua de horchata", List.of("horchata"));
        map.put("agua de jamaica", List.of("jamaica"));
        map.put("jugo de naranja natural", List.of("jugo de naranja", "naranja", "jugo"));
        map.put("cerveza artesanal", List.of("cerveza", "cheve", "chela", "artesanal"));
        map.put("margarita", List.of("margarita", "margi"));
        map.put("mezcal joven", List.of("mezcal", "mezcalito", "caballito"));
        map.put("refresco", List.of("refresco", "soda", "coca", "gaseosa"));
        map.put("guacamole con totopos", List.of("guacamole", "guac", "totopos", "aguacate"));
        map.put("nachos supremos", List.of("nachos", "totopos con queso"));
        map.put("sopa azteca", List.of("sopa azteca", "sopa de tortilla"));
        map.put("pozole rojo", List.of("pozole", "pozol"));
        map.put("elote en vaso", List.of("elote", "esquite"));
        map.put("sopes de frijol", List.of("sopes", "sopesitos"));
        map.put("crema de elote", List.of("crema de elote", "crema"));
        map.put("arrachera", List.of("arrachera", "arracheras"));
        map.put("tacos de nopal", List.of("nopal", "tacos nopal"));
        map.put("ensalada de nopal", List.of("ensalada nopal", "nopal ensalada"));
        PRODUCT_SYNONYMS = Collections.unmodifiableMap(map);
    }

    public ActionPlanEnricher(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public ActionPlan filterSpuriousActions(ActionPlan plan, String userMessage) {
        if (plan.actions() == null || plan.actions().isEmpty() || userMessage == null) return plan;

        List<ActionPlan.Action> filtered = new ArrayList<>();
        for (ActionPlan.Action a : plan.actions()) {
            if (a.type() != ActionPlan.ActionType.ADD_ITEM) {
                filtered.add(a);
                continue;
            }
            if (a.productId() == null) {
                log.info("Accion espuria filtrada: ADD_ITEM sin productId '{}'", a.productName());
                continue;
            }
            if (!productClearlyMentioned(userMessage, a)) {
                log.info("Accion espuria filtrada: '{}' no esta mencionado con claridad", a.productName());
                continue;
            }
            int qty = Math.min(Math.max(a.quantity(), 1), 10);
            if (qty != a.quantity()) {
                filtered.add(new ActionPlan.Action(a.type(), a.productId(), a.productName(), qty, a.modifiers(), a.reason()));
                log.warn("Cantidad ajustada de {} a {} para {}", a.quantity(), qty, a.productName());
            } else {
                filtered.add(a);
            }
        }

        return new ActionPlan(filtered, plan.responseMessage(), plan.confidence(),
            plan.requiresConfirmation(), plan.clarificationNeeded(), plan.clarificationMessage());
    }

    private boolean productClearlyMentioned(String userMessage, ActionPlan.Action action) {
        if (action.productName() == null || userMessage == null) return false;
        String msg = ConversationTextUtils.normalizeMessage(userMessage);
        if (java.util.regex.Pattern.compile("(?:id|producto\\s+id)\\s*[:#]?\\s*" + action.productId()).matcher(msg).find()) {
            return true;
        }
        String clean = ConversationTextUtils.normalizeProductName(action.productName());
        if (ConversationTextUtils.containsPhrase(msg, clean)) return true;
        for (String syn : PRODUCT_SYNONYMS.getOrDefault(clean, List.of())) {
            if (syn.length() >= 4 && !GENERIC_FOOD.contains(syn) && !NAME_STOPWORDS.contains(syn)
                && ConversationTextUtils.containsPhrase(msg, syn)) {
                return true;
            }
        }
        int specificHits = 0;
        for (String kw : clean.split("\\s+")) {
            if (kw.length() <= 3 || NAME_STOPWORDS.contains(kw) || GENERIC_FOOD.contains(kw)) continue;
            if (ConversationTextUtils.containsPhrase(msg, kw)) specificHits++;
        }
        return specificHits >= 1 && (clean.split("\\s+").length <= 2 || specificHits >= 1);
    }

    public ActionPlan enrichMissingActions(ActionPlan plan, String userMessage, List<ConversationTurn> history, String source, OrderSnapshot currentOrder) {
        if (userMessage == null) return plan;
        String msg = ConversationTextUtils.normalizeMessage(userMessage);
        if (msg.isBlank()) return plan;

        boolean isMenuInteractive = "MENU".equals(source);
        boolean isOptionsAsk = msg.contains("opcion") || msg.contains("tipo de") || msg.contains("tipos de") || msg.contains("cuales hay");
        boolean isOrdering = isMenuInteractive || ConversationTextUtils.matchesAny(msg,
            "quiero", "dame", "ponme", "traeme", "pido", "pedir", "deme", "agrega", "agregame", "anade", "anadame",
            "voy a pedir", "vamos a pedir", "me das", "me traes", "me pones", "anotame", "apuntame", "para mi",
            "de tomar", "de comer", "tambien", "una orden");
        boolean isRemovingItem = ConversationTextUtils.matchesAny(msg,
            "ya no quiero", "no quiero", "quita", "borra", "saca", "elimina", "retira", "remueve", "quitar", "sacar", "quite");
        List<String> orderSegments = ConversationTextUtils.splitOrderSegments(userMessage);
        boolean multiItemOrder = countDistinctProducts(msg) >= 2 || orderSegments.size() >= 2;
        if (multiItemOrder && !isRemovingItem) isOrdering = true;
        log.info("enrichMissingActions: msg='{}' isOrdering={} isRemovingItem={} multi={} source={}", msg, isOrdering, isRemovingItem, multiItemOrder, source);

        if (!(isOrdering && multiItemOrder)) {
            ActionPlan ingredientEdit = resolveIngredientEdit(plan, msg, currentOrder, isOrdering, isMenuInteractive);
            if (ingredientEdit != null) {
                log.info("Ingrediente detectado, no se elimina el producto: {}", ingredientEdit.actions());
                return ingredientEdit;
            }
        }

        if (!isMenuInteractive && isOptionsAsk && !isRemovingItem) return plan;
        if (!isOrdering && !isRemovingItem) return plan;

        List<ActionPlan.Action> actions = new ArrayList<>(plan.actions() != null ? plan.actions() : List.of());
        Set<Long> existingProductIds = new HashSet<>();
        for (ActionPlan.Action a : actions) if (a.productId() != null) existingProductIds.add(a.productId());
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern
            .compile("(?i)(?:id|producto\\s+id)\\s*[:#]?\\s*(\\d+)")
            .matcher(msg);
        Long explicitProductId = null;
        if (idMatcher.find()) {
            explicitProductId = Long.parseLong(idMatcher.group(1));
            Long pid = explicitProductId;
            String productName = null;
            for (var cat : catalogService.getFullMenu()) {
                for (var p : cat.products()) {
                    if (pid.equals(p.id())) {
                        productName = p.name();
                        break;
                    }
                }
                if (productName != null) break;
            }
            if (isMenuInteractive && !isRemovingItem) {
                actions.removeIf(a -> a.type() == ActionPlan.ActionType.ADD_ITEM);
                existingProductIds.clear();
            }
            if (!isRemovingItem && !existingProductIds.contains(pid)) {
                int qty = extractQuantity(msg, "id", history);
                CatalogService.ProductDTO product = null;
                for (var cat : catalogService.getFullMenu()) {
                    for (var p : cat.products()) {
                        if (pid.equals(p.id())) {
                            product = p;
                            break;
                        }
                    }
                    if (product != null) break;
                }
                actions.add(new ActionPlan.Action(ActionPlan.ActionType.ADD_ITEM, pid, productName, Math.max(1, qty), product != null ? extractModifiers(msg, product) : List.of(), "detectado por ID"));
                existingProductIds.add(pid);
            }
            if (isMenuInteractive && !isRemovingItem) {
                return new ActionPlan(actions, plan.responseMessage(), plan.confidence(),
                    false, false, null);
            }
        }

        if (!isMenuInteractive && isRemovingItem && actions.stream().noneMatch(a -> a.type() == ActionPlan.ActionType.REMOVE_ITEM)) {
            ActionPlan.Action removeAction = resolveRemoveAction(msg, history, actions);
            if (removeAction != null) actions.add(removeAction);
        }

        if (isRemovingItem) {
            actions.removeIf(a -> a.type() == ActionPlan.ActionType.ADD_ITEM);
            return new ActionPlan(actions, plan.responseMessage(), plan.confidence(),
                plan.requiresConfirmation(), plan.clarificationNeeded(), plan.clarificationMessage());
        }

        List<ActionPlan.Action> detectedAdds = collectAddActionsFromSegments(orderSegments, history);
        if (isOrdering) {
            List<ActionPlan.Action> fromText = collectAddActionsFromText(msg, history);
            if (fromText.size() > detectedAdds.size()) detectedAdds = fromText;
        }
        if (!detectedAdds.isEmpty()) {
            Set<Long> seen = new HashSet<>(existingProductIds);
            for (ActionPlan.Action add : detectedAdds) {
                if (add.productId() == null || seen.contains(add.productId())) continue;
                actions.add(add);
                seen.add(add.productId());
            }
            if (detectedAdds.size() >= 2) {
                return new ActionPlan(actions, plan.responseMessage(), plan.confidence(), false, false, null);
            }
        }

        var menu = catalogService.getFullMenu();
        for (var cat : menu) {
            for (var p : cat.products()) {
                if (isMenuInteractive && explicitProductId != null && !explicitProductId.equals(p.id())) continue;
                if (existingProductIds.contains(p.id())) continue;
                boolean alreadyInPlan = actions.stream()
                    .anyMatch(a -> a.type() == ActionPlan.ActionType.ADD_ITEM && p.id().equals(a.productId()));
                if (alreadyInPlan) continue;
                MatchResult mr = matchProduct(p, msg, history);
                if (mr.matched) {
                    List<ActionPlan.Modifier> mods = extractModifiers(msg, p);
                    actions.add(new ActionPlan.Action(ActionPlan.ActionType.ADD_ITEM, p.id(), p.name(), mr.qty, mods, "detectado del mensaje"));
                    log.info("Enriquecido: ADD_ITEM {} x{} (msg='{}')", p.name(), mr.qty, msg);
                }
            }
        }

        actions = disambiguateActions(actions, msg);
        if (actions.stream().filter(a -> a.type() == ActionPlan.ActionType.ADD_ITEM).count() <= 1) {
            actions = filterWeakPartialMatches(actions, msg);
        }

        if (!actions.equals(plan.actions() != null ? plan.actions() : List.of())) {
            return new ActionPlan(actions, plan.responseMessage(), plan.confidence(),
                plan.requiresConfirmation(), plan.clarificationNeeded(), plan.clarificationMessage());
        }
        return plan;
    }

    public String buildConfirmationMessage(List<ActionPlan.Action> actions) {
        if (actions == null || actions.isEmpty()) return "Entendido!";

        List<ActionPlan.Action> adds = new ArrayList<>();
        List<ActionPlan.Action> modifies = new ArrayList<>();
        List<ActionPlan.Action> removes = new ArrayList<>();

        for (ActionPlan.Action a : actions) {
            switch (a.type()) {
                case ADD_ITEM -> adds.add(a);
                case MODIFY_QUANTITY -> modifies.add(a);
                case REMOVE_ITEM -> removes.add(a);
                case ADD_MODIFIER, REMOVE_MODIFIER -> modifies.add(a);
                default -> {}
            }
        }

        StringBuilder result = new StringBuilder();

        if (!adds.isEmpty()) {
            result.append(buildItemLinesMessage(groupAndSum(adds), true));
        }
        if (!modifies.isEmpty()) {
            if (!result.isEmpty()) result.append(" ");
            result.append(buildItemLinesMessage(groupAndSum(modifies), false));
        }
        if (!removes.isEmpty()) {
            if (!result.isEmpty()) result.append(" ");
            result.append(buildRemovalMessage(removes));
        }

        return result.isEmpty() ? "Entendido!" : result.toString();
    }

    private record MatchResult(boolean matched, int qty) {}

    private static class ItemLine {
        final String name;
        int qty;
        final List<ActionPlan.Modifier> modifiers;

        ItemLine(String name, int qty, List<ActionPlan.Modifier> modifiers) {
            this.name = name;
            this.qty = qty;
            this.modifiers = modifiers != null ? modifiers : List.of();
        }
    }

    private MatchResult matchProduct(CatalogService.ProductDTO p, String msg, List<ConversationTurn> history) {
        String clean = ConversationTextUtils.normalizeProductName(p.name());
        String[] keywords = clean.split("\\s+");

        List<String> synonyms = PRODUCT_SYNONYMS.getOrDefault(clean, List.of());
        for (String syn : synonyms) {
            if (ConversationTextUtils.containsPhrase(msg, syn)) {
                log.info("matchProduct: '{}' matched synonym '{}' for product '{}'", msg, syn, p.name());
                return new MatchResult(true, extractQuantity(msg, syn, history));
            }
        }

        int matches = 0;
        boolean hasSpecific = false;
        for (String kw : keywords) {
            if (kw.length() <= 2 || NAME_STOPWORDS.contains(kw)) continue;
            String stem = kw.length() > 3 ? kw.replaceAll("s$", "") : kw;
            if (ConversationTextUtils.containsPhrase(msg, kw) || ConversationTextUtils.containsPhrase(msg, stem)) {
                matches++;
                if (kw.length() > 4 && !GENERIC_FOOD.contains(kw) && !GENERIC_FOOD.contains(stem)) hasSpecific = true;
            }
        }
        boolean matched = hasSpecific || matches >= 2 || (clean.length() > 3 && ConversationTextUtils.containsPhrase(msg, clean));
        if (!matched) return new MatchResult(false, 1);

        return new MatchResult(true, extractQuantity(msg, clean, history));
    }

    private int countDistinctProducts(String text) {
        return findProductsInText(text, 16).size();
    }

    private List<CatalogService.ProductDTO> findProductsInText(String text, int minScore) {
        record Scored(CatalogService.ProductDTO product, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (var cat : catalogService.getFullMenu()) {
            for (var p : cat.products()) {
                int score = scoreProduct(text, p);
                if (score >= minScore) scored.add(new Scored(p, score));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        Map<String, CatalogService.ProductDTO> byFamily = new LinkedHashMap<>();
        Map<String, Integer> familyScore = new HashMap<>();
        for (Scored s : scored) {
            String family = ConversationTextUtils.normalizeProductName(s.product.name()).split("\\s+")[0];
            if (!byFamily.containsKey(family) || s.score > familyScore.getOrDefault(family, 0)) {
                byFamily.put(family, s.product);
                familyScore.put(family, s.score);
            }
        }
        return new ArrayList<>(byFamily.values());
    }

    private int scoreProduct(String text, CatalogService.ProductDTO p) {
        String clean = ConversationTextUtils.normalizeProductName(p.name());
        int score = 0;
        if (clean.length() > 3 && ConversationTextUtils.containsPhrase(text, clean)) score = Math.max(score, 40);
        for (String syn : PRODUCT_SYNONYMS.getOrDefault(clean, List.of())) {
            if (syn.length() >= 4 && ConversationTextUtils.containsPhrase(text, syn)) {
                if (GENERIC_FOOD.contains(syn) || NAME_STOPWORDS.contains(syn)) score = Math.max(score, 8);
                else score = Math.max(score, syn.length() >= 6 ? 18 : 14);
            }
        }
        int hits = 0;
        boolean specific = false;
        for (String kw : clean.split("\\s+")) {
            if (kw.length() <= 2 || NAME_STOPWORDS.contains(kw)) continue;
            String stem = kw.length() > 3 ? kw.replaceAll("s$", "") : kw;
            if (ConversationTextUtils.containsPhrase(text, kw) || ConversationTextUtils.containsPhrase(text, stem)) {
                hits++;
                if (kw.length() > 4 && !GENERIC_FOOD.contains(kw)) specific = true;
            }
        }
        if (specific) score = Math.max(score, 16 + hits);
        else if (hits >= 2) score = Math.max(score, 14);
        return score;
    }

    private List<ActionPlan.Action> collectAddActionsFromSegments(List<String> segments, List<ConversationTurn> history) {
        if (segments == null || segments.isEmpty()) return List.of();
        List<String> merged = new ArrayList<>();
        for (String seg : segments) {
            if (findProductsInText(seg, 12).isEmpty() && !merged.isEmpty()) {
                merged.set(merged.size() - 1, merged.get(merged.size() - 1) + " y " + seg);
            } else {
                merged.add(seg);
            }
        }
        List<ActionPlan.Action> adds = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (String seg : merged) {
            List<CatalogService.ProductDTO> hits = findProductsInText(seg, 12);
            if (hits.isEmpty()) continue;
            for (CatalogService.ProductDTO hit : hits) {
                if (used.contains(hit.id())) continue;
                used.add(hit.id());
                int qty = extractQuantity(seg, ConversationTextUtils.normalizeProductName(hit.name()), history);
                adds.add(new ActionPlan.Action(ActionPlan.ActionType.ADD_ITEM, hit.id(), hit.name(), qty,
                    extractModifiers(seg, hit), "detectado en el pedido"));
            }
        }
        return adds;
    }

    private List<ActionPlan.Action> collectAddActionsFromText(String msg, List<ConversationTurn> history) {
        List<ActionPlan.Action> adds = new ArrayList<>();
        for (CatalogService.ProductDTO p : findProductsInText(msg, 16)) {
            int qty = extractQuantity(msg, ConversationTextUtils.normalizeProductName(p.name()), history);
            adds.add(new ActionPlan.Action(ActionPlan.ActionType.ADD_ITEM, p.id(), p.name(), qty,
                extractModifiersNearProduct(msg, p), "detectado en el pedido"));
        }
        return adds;
    }

    private List<ActionPlan.Modifier> extractModifiersNearProduct(String msg, CatalogService.ProductDTO product) {
        String clean = ConversationTextUtils.normalizeProductName(product.name());
        int idx = msg.indexOf(clean);
        if (idx < 0) {
            for (String syn : PRODUCT_SYNONYMS.getOrDefault(clean, List.of())) {
                idx = msg.indexOf(syn);
                if (idx >= 0) break;
            }
        }
        if (idx < 0) return extractModifiers(msg, product);
        int start = Math.max(0, idx - 8);
        int end = Math.min(msg.length(), idx + Math.max(clean.length(), 12) + 40);
        return extractModifiers(msg.substring(start, end), product);
    }

    private int extractQuantity(String msg, String productContext, List<ConversationTurn> history) {
        if (java.util.regex.Pattern.compile("\\b(un|una|unos|unas)\\b").matcher(msg).find()
            && !java.util.regex.Pattern.compile("\\b(\\d+|dos|doble|tres|triple|cuatro)\\b").matcher(msg).find()) {
            return 1;
        }
        java.util.regex.Matcher prefix = java.util.regex.Pattern
            .compile("^(?:quiero|dame|ponme|traeme|agrega|anade|pido)?\\s*(\\d+)\\s+")
            .matcher(msg);
        if (prefix.find()) {
            try {
                int n = Integer.parseInt(prefix.group(1));
                if (n >= 1 && n <= 10) return n;
            } catch (Exception ignored) {}
        }

        if (productContext != null && !productContext.isBlank()) {
            String escaped = java.util.regex.Pattern.quote(productContext);
            java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d+)\\s+(?:" + escaped.replace(" ", "\\s+") + ")")
                .matcher(msg);
            if (m.find()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n >= 1 && n <= 10) return n;
                } catch (Exception ignored) {}
            }
        }

        if (java.util.regex.Pattern.compile("\\b(doble|dos)\\b").matcher(msg).find()) return 2;
        if (java.util.regex.Pattern.compile("\\b(triple|tres)\\b").matcher(msg).find()) return 3;
        if (java.util.regex.Pattern.compile("\\b(cuatro)\\b").matcher(msg).find()) return 4;

        return 1;
    }

    private List<ActionPlan.Modifier> extractModifiers(String msg, CatalogService.ProductDTO product) {
        List<ActionPlan.Modifier> mods = new ArrayList<>();
        var ingredients = product.ingredients();
        if (ingredients == null) return mods;

        boolean removalCue = ConversationTextUtils.matchesAny(msg,
            "sin ", "sin el ", "sin la ", "sin los ", "sin las ", "quita", "quitar", "quite", "quiten",
            "quitale", "quitalo", "sacale", "saca el", "saca los", "saca la", "no le pongas", "sin nada de",
            "no quiero", "ya no quiero");
        boolean addCue = ConversationTextUtils.matchesAny(msg,
            "con extra", "extra ", "ponle", "agregale", "con mas ", "doble ", "con el ", "con la ", "con los ");

        for (var ing : ingredients) {
            if (ing.type() == null) continue;
            if (!ConversationTextUtils.mentionsToken(msg, ing.name())) continue;

            if ("OPTIONAL".equals(ing.type()) && addCue) {
                mods.add(new ActionPlan.Modifier(ActionPlan.ModifierType.ADD, ing.id(), ing.name()));
                continue;
            }
            if (removalCue && "REMOVABLE".equals(ing.type())) {
                mods.add(new ActionPlan.Modifier(ActionPlan.ModifierType.REMOVE, ing.id(), ing.name()));
            }
        }
        return mods;
    }

    private ActionPlan resolveIngredientEdit(ActionPlan plan, String msg, OrderSnapshot currentOrder,
                                            boolean isOrdering, boolean isMenuInteractive) {
        boolean removalCue = ConversationTextUtils.matchesAny(msg,
            "sin ", "sin el ", "sin la ", "sin los ", "sin las ", "quita", "quitar", "quite", "quiten",
            "quitale", "quitalo", "sacale", "saca el", "saca los", "no le pongas", "sin nada de",
            "no quiero", "ya no quiero");
        boolean addCue = ConversationTextUtils.matchesAny(msg,
            "con extra", "extra ", "ponle", "agregale", "con mas ", "doble ");
        if (!removalCue && !addCue) return null;
        if (isMenuInteractive && isOrdering && !removalCue && !addCue) return null;

        CatalogService.ProductDTO mentioned = findMentionedProduct(msg);
        List<CatalogService.ProductDTO> candidates = new ArrayList<>();
        if (mentioned != null) candidates.add(mentioned);
        if (currentOrder != null && currentOrder.items() != null) {
            for (var item : currentOrder.items()) {
                CatalogService.ProductDTO dto = findProductById(item.productId());
                if (dto != null && candidates.stream().noneMatch(p -> p.id().equals(dto.id()))) {
                    candidates.add(dto);
                }
            }
        }
        if (candidates.isEmpty()) return null;

        CatalogService.ProductDTO target = null;
        List<ActionPlan.Modifier> mods = List.of();
        if (mentioned != null) {
            mods = extractModifiers(msg, mentioned);
            if (!mods.isEmpty()) target = mentioned;
        }
        if (target == null) {
            for (CatalogService.ProductDTO candidate : candidates) {
                List<ActionPlan.Modifier> found = extractModifiers(msg, candidate);
                if (!found.isEmpty()) {
                    target = candidate;
                    mods = found;
                    break;
                }
            }
        }
        if (target == null || mods.isEmpty()) {
            CatalogService.ProductDTO blockedProduct = mentioned != null ? mentioned : (candidates.isEmpty() ? null : candidates.get(0));
            if (blockedProduct != null && hasRemovalCueForBaseOnly(msg, blockedProduct, currentOrder)) {
                String blocked = blockedBaseNames(msg, blockedProduct);
                if (!blocked.isBlank() && !messageNamesWholeProduct(msg, blockedProduct)) {
                    String reply = "No puedo quitar " + blocked + " de " + blockedProduct.name()
                        + ": es un ingrediente obligatorio del plato. Sí puedo quitar ingredientes opcionales, como cilantro o jalapeño.";
                    return new ActionPlan(List.of(), reply, 1.0, false, false, null);
                }
            }
            return null;
        }

        String title = ConversationTextUtils.normalizeProductName(target.name());
        mods = mods.stream()
            .filter(m -> m.type() != ActionPlan.ModifierType.REMOVE
                || !ConversationTextUtils.mentionsToken(title, m.ingredientName()))
            .toList();
        if (mods.isEmpty()) return null;

        final CatalogService.ProductDTO selected = target;
        boolean inCart = currentOrder != null && currentOrder.items() != null
            && currentOrder.items().stream().anyMatch(i -> selected.id().equals(i.productId()));

        List<ActionPlan.Action> actions = new ArrayList<>(plan.actions() != null ? plan.actions() : List.of());
        actions.removeIf(a -> a.type() == ActionPlan.ActionType.ADD_ITEM
            || a.type() == ActionPlan.ActionType.REMOVE_ITEM);

        if (inCart) {
            boolean anyRemove = mods.stream().anyMatch(m -> m.type() == ActionPlan.ModifierType.REMOVE);
            ActionPlan.ActionType type = anyRemove ? ActionPlan.ActionType.REMOVE_MODIFIER : ActionPlan.ActionType.ADD_MODIFIER;
            actions.add(new ActionPlan.Action(type, selected.id(), selected.name(), 1, mods, "modificar ingredientes del pedido"));
            return new ActionPlan(actions, plan.responseMessage(), 1.0, false, false, null);
        }

        if (isOrdering || isMenuInteractive) {
            return null;
        }

        actions.add(new ActionPlan.Action(ActionPlan.ActionType.ADD_ITEM, selected.id(), selected.name(), 1, mods, "producto con ingredientes ajustados"));
        return new ActionPlan(actions, plan.responseMessage(), 1.0, false, false, null);
    }

    private boolean messageNamesWholeProduct(String msg, CatalogService.ProductDTO product) {
        String clean = ConversationTextUtils.normalizeProductName(product.name());
        return ConversationTextUtils.containsPhrase(msg, clean);
    }

    private String blockedBaseNames(String msg, CatalogService.ProductDTO product) {
        if (product.ingredients() == null) return "";
        List<String> names = new ArrayList<>();
        for (var ing : product.ingredients()) {
            if (!"BASE".equals(ing.type())) continue;
            if (ConversationTextUtils.mentionsToken(msg, ing.name())
                && !ConversationTextUtils.mentionsToken(ConversationTextUtils.normalizeProductName(product.name()), ing.name())) {
                names.add(ing.name().toLowerCase());
            } else if (ConversationTextUtils.mentionsToken(msg, ing.name())
                && !messageNamesWholeProduct(msg, product)) {
                names.add(ing.name().toLowerCase());
            }
        }
        return String.join(" y ", names);
    }

    private boolean hasRemovalCueForBaseOnly(String msg, CatalogService.ProductDTO product, OrderSnapshot currentOrder) {
        boolean removalCue = ConversationTextUtils.matchesAny(msg,
            "sin ", "quita", "quitar", "quite", "saca", "no le pongas", "no quiero");
        if (!removalCue) return false;
        boolean inCart = currentOrder != null && currentOrder.items() != null
            && currentOrder.items().stream().anyMatch(i -> product.id().equals(i.productId()));
        return inCart || ConversationTextUtils.mentionsToken(msg, ConversationTextUtils.normalizeProductName(product.name()).split(" ")[0]);
    }

    private CatalogService.ProductDTO findProductById(Long id) {
        if (id == null) return null;
        for (var cat : catalogService.getFullMenu()) {
            for (var p : cat.products()) {
                if (id.equals(p.id())) return p;
            }
        }
        return null;
    }

    private CatalogService.ProductDTO findMentionedProduct(String msg) {
        CatalogService.ProductDTO best = null;
        int bestScore = 0;
        for (var cat : catalogService.getFullMenu()) {
            for (var p : cat.products()) {
                int score = 0;
                String clean = ConversationTextUtils.normalizeProductName(p.name());
                if (clean.length() > 3 && ConversationTextUtils.containsPhrase(msg, clean)) score = Math.max(score, 20);
                for (String syn : PRODUCT_SYNONYMS.getOrDefault(clean, List.of())) {
                    if (syn.length() >= 4 && ConversationTextUtils.containsPhrase(msg, syn)) {
                        score = Math.max(score, syn.length() >= 6 ? 16 : 10);
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return bestScore >= 10 ? best : null;
    }

    private ActionPlan.Action resolveRemoveAction(String msg, List<ConversationTurn> history, List<ActionPlan.Action> currentActions) {
        boolean vague = msg.contains("eso") || msg.contains("ultimo") || msg.contains("lo ultimo");
        if (vague && !currentActions.isEmpty()) {
            for (int i = currentActions.size() - 1; i >= 0; i--) {
                ActionPlan.Action a = currentActions.get(i);
                if (a.type() == ActionPlan.ActionType.ADD_ITEM && a.productId() != null) {
                    return new ActionPlan.Action(ActionPlan.ActionType.REMOVE_ITEM, a.productId(), a.productName(), a.quantity(), List.of(), "referencia vaga del usuario");
                }
            }
        }
        var menu = catalogService.getFullMenu();
        for (var cat : menu) {
            for (var p : cat.products()) {
                String clean = ConversationTextUtils.normalizeProductName(p.name());
                boolean matched = ConversationTextUtils.containsPhrase(msg, clean);
                if (!matched) {
                    for (String synonym : PRODUCT_SYNONYMS.getOrDefault(clean, List.of())) {
                        if (ConversationTextUtils.containsPhrase(msg, synonym)) {
                            matched = true;
                            break;
                        }
                    }
                }
                if (!matched) {
                    String[] words = clean.split("\\s+");
                    int matches = 0;
                    for (String word : words) {
                        if (word.length() > 3 && !NAME_STOPWORDS.contains(word)
                            && ConversationTextUtils.containsPhrase(msg, word.replaceAll("s$", ""))) matches++;
                    }
                    matched = matches >= 2;
                }
                if (matched) {
                    int qty = 1;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s+(?:" + clean.replace(" ", "\\s+") + ")").matcher(msg);
                    if (m.find()) try { qty = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
                    return new ActionPlan.Action(ActionPlan.ActionType.REMOVE_ITEM, p.id(), p.name(), qty, List.of(), "detectado del mensaje");
                }
            }
        }
        return null;
    }

    private List<ActionPlan.Action> disambiguateActions(List<ActionPlan.Action> actions, String msg) {
        if (actions.size() <= 1) return actions;
        Map<String, List<ActionPlan.Action>> byBase = new HashMap<>();
        for (ActionPlan.Action a : actions) {
            String base = a.productName().toLowerCase().split(" ")[0];
            byBase.computeIfAbsent(base, k -> new ArrayList<>()).add(a);
        }
        List<ActionPlan.Action> result = new ArrayList<>();
        for (List<ActionPlan.Action> group : byBase.values()) {
            if (group.size() == 1) {
                result.add(group.get(0));
            } else {
                ActionPlan.Action best = group.get(0);
                int bestScore = -1;
                for (ActionPlan.Action a : group) {
                    String[] words = a.productName().toLowerCase().split(" ");
                    int score = 0;
                    for (String w : words) {
                        if (w.length() > 2 && !NAME_STOPWORDS.contains(w) && ConversationTextUtils.containsPhrase(msg, w)) score++;
                    }
                    if (score > bestScore) { bestScore = score; best = a; }
                }
                result.add(best);
            }
        }
        return result;
    }

    private List<ActionPlan.Action> filterWeakPartialMatches(List<ActionPlan.Action> actions, String msg) {
        if (actions.size() <= 1) return actions;
        List<ActionPlan.Action> addItems = actions.stream()
            .filter(a -> a.type() == ActionPlan.ActionType.ADD_ITEM)
            .toList();
        if (addItems.size() <= 1) return actions;

        Map<ActionPlan.Action, Integer> scores = new HashMap<>();
        for (ActionPlan.Action a : addItems) {
            int score = 0;
            for (String word : a.productName().toLowerCase().split(" ")) {
                if (word.length() > 2 && !NAME_STOPWORDS.contains(word) && ConversationTextUtils.containsPhrase(msg, word)) score++;
            }
            scores.put(a, score);
        }
        int maxScore = scores.values().stream().max(Integer::compare).orElse(0);
        if (maxScore <= 1) return actions;

        long countAtMax = addItems.stream().filter(a -> scores.get(a) == maxScore).count();

        List<ActionPlan.Action> result = new ArrayList<>();
        for (ActionPlan.Action a : actions) {
            if (a.type() != ActionPlan.ActionType.ADD_ITEM) {
                result.add(a);
            } else if (scores.get(a) == maxScore) {
                result.add(a);
            } else if (scores.get(a) >= maxScore - 1 && countAtMax > 1) {
                result.add(a);
            } else {
                log.info("Filtrado match debil: {} (score={} vs max={})", a.productName(), scores.get(a), maxScore);
            }
        }
        return result;
    }

    private List<ItemLine> groupAndSum(List<ActionPlan.Action> actions) {
        Map<String, ItemLine> map = new LinkedHashMap<>();
        for (ActionPlan.Action a : actions) {
            String key = a.productId() + "|" + formatModifiersKey(a.modifiers());
            ItemLine existing = map.get(key);
            if (existing != null) {
                existing.qty += a.quantity();
            } else {
                map.put(key, new ItemLine(a.productName(), a.quantity(), a.modifiers()));
            }
        }
        return new ArrayList<>(map.values());
    }

    private String formatModifiersKey(List<ActionPlan.Modifier> mods) {
        if (mods == null || mods.isEmpty()) return "";
        return mods.stream()
            .map(m -> m.type().name() + "=" + m.ingredientName())
            .sorted()
            .collect(Collectors.joining(","));
    }

    private String buildItemLinesMessage(List<ItemLine> lines, boolean isNew) {
        if (lines.isEmpty()) return "";
        if (lines.size() == 1) {
            ItemLine line = lines.get(0);
            String itemText = formatItemLine(line);
            if (isNew) {
                String[] intros = {"Anotado", "Listo", "Agregué", "Añadí", "Perfecto"};
                String intro = intros[(int)(Math.random() * intros.length)];
                return intro + ": " + itemText + ".";
            } else {
                String[] intros = {"Listo, actualicé tu", "Actualicé tu", "Cambié tu"};
                String intro = intros[(int)(Math.random() * intros.length)];
                StringBuilder sb = new StringBuilder(intro + " " + line.name);
                String mods = formatModifiers(line.modifiers);
                if (!mods.isEmpty()) sb.append(mods);
                sb.append(".");
                return sb.toString();
            }
        }
        if (isNew) {
            String[] intros = {"Listo, añadí", "Anotado", "Perfecto, agregué", "Listo, incluí"};
            String intro = intros[(int)(Math.random() * intros.length)];
            StringBuilder sb = new StringBuilder(intro + " ");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append(i == lines.size() - 1 ? " y " : ", ");
                sb.append(formatItemLine(lines.get(i)));
            }
            sb.append(".");
            return sb.toString();
        } else {
            String[] intros = {"Actualicé", "Listo, cambié"};
            String intro = intros[(int)(Math.random() * intros.length)];
            StringBuilder sb = new StringBuilder(intro + " ");
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) sb.append(i == lines.size() - 1 ? " y " : ", ");
                sb.append(formatItemLine(lines.get(i)));
            }
            sb.append(".");
            return sb.toString();
        }
    }

    private String formatItemLine(ItemLine line) {
        StringBuilder sb = new StringBuilder();
        if (line.qty > 1) sb.append(line.qty).append(" ");
        sb.append(line.name);
        String mods = formatModifiers(line.modifiers);
        if (!mods.isEmpty()) sb.append(mods);
        return sb.toString();
    }

    private String buildRemovalMessage(List<ActionPlan.Action> removes) {
        if (removes.size() == 1) {
            String[] intros = {"Eliminé", "Quité", "Retiré"};
            String intro = intros[(int)(Math.random() * intros.length)];
            return intro + " " + removes.get(0).productName() + " de tu pedido.";
        }
        StringBuilder sb = new StringBuilder("Listo, quité ");
        for (int i = 0; i < removes.size(); i++) {
            if (i > 0) sb.append(i == removes.size() - 1 ? " y " : ", ");
            sb.append(removes.get(i).productName());
        }
        sb.append(" de tu pedido.");
        return sb.toString();
    }

    private String formatModifiers(List<ActionPlan.Modifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return "";
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        for (ActionPlan.Modifier m : modifiers) {
            if (m.type() == ActionPlan.ModifierType.REMOVE) removed.add(m.ingredientName());
            else if (m.type() == ActionPlan.ModifierType.ADD) added.add(m.ingredientName());
        }
        StringBuilder sb = new StringBuilder();
        if (!removed.isEmpty()) sb.append(" sin ").append(String.join(", ", removed));
        if (!added.isEmpty()) sb.append(" con ").append(String.join(", ", added));
        return sb.toString();
    }
}
