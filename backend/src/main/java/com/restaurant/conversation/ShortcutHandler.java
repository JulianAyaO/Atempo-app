package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.common.dto.OrderSnapshot;
import com.restaurant.common.exception.InvalidActionPlanException;
import com.restaurant.orders.Order;
import com.restaurant.orders.OrderService;
import com.restaurant.realtime.RealtimeService;
import org.springframework.stereotype.Component;

@Component
public class ShortcutHandler {

    private final CatalogService catalogService;
    private final OrderService orderService;
    private final RealtimeService realtimeService;

    public ShortcutHandler(CatalogService catalogService, OrderService orderService, RealtimeService realtimeService) {
        this.catalogService = catalogService;
        this.orderService = orderService;
        this.realtimeService = realtimeService;
    }

    /**
     * Maneja intenciones comunes sin llamar al LLM (mas rapido y confiable).
     */
    public String handleShortcut(String message, Order draftOrder, java.util.List<ConversationTurn> history) {
        if (message == null) return null;
        String m = ConversationTextUtils.normalizeMessage(message);
        OrderSnapshot snap = orderService.toSnapshot(draftOrder);

        if (ConversationTextUtils.matchesAny(m, "confirmar pedido", "confirma pedido", "confirmar el pedido", "confirma el pedido", "confirmalo", "confirmar orden", "confirma orden")) {
            if (snap.items().isEmpty()) {
                return "Tu pedido esta vacio. Agrega productos antes de confirmar.";
            }
            if (!"DRAFT".equals(snap.status())) {
                return "Tu pedido ya no esta en borrador. Estado actual: " + snap.status() + ".";
            }
            Order confirmed = orderService.confirmOrder(draftOrder.getId());
            return "Pedido confirmado y enviado a cocina.\n\n" + formatOrderSnapshot(orderService.toSnapshot(confirmed));
        }

        if (ConversationTextUtils.matchesAny(m,
            "vaciar pedido", "vacia pedido", "vaciar el pedido", "vacia el pedido",
            "vaciar carrito", "vacia carrito", "vaciar el carrito", "vacia el carrito",
            "borrar carrito", "borra carrito", "borra el carrito", "borrar el carrito",
            "limpiar carrito", "limpia carrito", "limpia el carrito", "limpiar el carrito",
            "ya no quiero nada del carrito", "ya no quiero nada del pedido", "ya no quiero nada",
            "no quiero nada del carrito", "no quiero nada del pedido",
            "quita todo del carrito", "quita todo del pedido", "borra todo del carrito",
            "cancelar pedido", "cancela pedido", "cancelar el pedido", "cancela el pedido",
            "cancelar carrito", "cancela carrito", "cancelar el carrito", "cancela el carrito")) {
            if (snap.items().isEmpty()) {
                return "Tu pedido ya esta vacio.";
            }
            if (!"DRAFT".equals(snap.status())) {
                return "Solo puedo vaciar el pedido mientras esta en borrador. Estado actual: " + snap.status() + ".";
            }
            Order cleared = orderService.clearDraftItems(draftOrder.getId());
            return "Listo, vacie todos los productos del carrito.\n\n" + formatOrderSnapshot(orderService.toSnapshot(cleared));
        }

        if (ConversationTextUtils.matchesAny(m, "hola", "buenos dias", "buenas tardes", "buenas noches", "hey", "que tal", "como estas", "saludos", "buenas")) {
            return "Hola! Soy tu asistente de mesa. Puedes pedir del menu, preguntar precios, o decirme que te gustaria. ¿En que puedo ayudarte?";
        }

        if (isOptionsRequest(m)) {
            String topic = resolveTopic(m, history);
            if (topic != null) {
                String focused = buildCategoryOptions(topic);
                if (focused != null) return focused;
            }
            if (isExplicitFullMenuRequest(m)) {
                return buildMenuResponse();
            }
            return null;
        }

        if (isExplicitFullMenuRequest(m)) {
            return buildMenuResponse();
        }

        if (ConversationTextUtils.matchesAny(m, "quiero pagar", "quiero la cuenta", "la cuenta", "la cuenta por favor", "nos vamos", "ya nos vamos", "terminamos",
            "ya estamos", "podemos pagar", "me trae la cuenta", "pagar")) {
            if (snap.items().isEmpty()) {
                return "Tu pedido esta vacio. Aun no hay nada que pagar. ¿Te gustaria pedir algo?";
            }
            try {
                Order paymentOrder = orderService.requestPaymentBySession(draftOrder.getSessionId(), draftOrder.getTableId());
                return "Cuenta solicitada. Un mesero vendra a tu mesa para cobrar.\n\n"
                    + formatOrderSnapshot(orderService.toSnapshot(paymentOrder));
            } catch (InvalidActionPlanException e) {
                return e.getMessage() + "\n\n" + formatOrderSnapshot(snap);
            } catch (RuntimeException e) {
                return e.getMessage() + "\n\n" + formatOrderSnapshot(snap);
            }
        }

        if (ConversationTextUtils.matchesAny(m, "total", "cuanto es", "cuanto cuesta", "precio", "muestrame el pedido", "que pedi", "mi pedido",
            "cuanto debo", "cuanto es todo", "cuanto salio")) {
            if (snap.items().isEmpty()) {
                return "Tu pedido esta vacio. Escribe lo que deseas pedir o pide 'dame el menu' para ver las opciones.";
            }
            return formatOrderSnapshot(snap);
        }

        if (ConversationTextUtils.matchesAny(m, "mesero", "llamar mesero", "necesito ayuda", "ayuda", "ven", "rapido", "atencion",
            "alguien", "mesera", "garzon", "garzona", "necesito un mesero", "puede venir alguien", "no entiendo")) {
            realtimeService.notifyWaiterCall(draftOrder.getTableId(), message);
            return "He llamado al mesero. En un momento vendra a ayudarte.";
        }

        if (ConversationTextUtils.matchesAny(m, "olvidalo todo", "reinicia", "empezar de cero",
            "borra todo", "quita todo", "elimina todo", "cancela todo")) {
            if (snap.items().isEmpty()) {
                return "Tu pedido ya esta vacio.";
            }
            if (!"DRAFT".equals(snap.status())) {
                return "Solo puedo vaciar el pedido mientras esta en borrador. Estado actual: " + snap.status() + ".";
            }
            Order cleared = orderService.clearDraftItems(draftOrder.getId());
            return "Listo, vacie todos los productos del carrito.\n\n" + formatOrderSnapshot(orderService.toSnapshot(cleared));
        }

        if (ConversationTextUtils.matchesAny(m, "adios", "chao", "gracias", "hasta luego", "nos vemos", "bye", "adiosito")) {
            return "Gracias por visitarnos! Si necesitas algo mas, aqui estoy.";
        }

        if (ConversationTextUtils.matchesAny(m, "que me recomiendas", "recomiendame", "recomiendame algo", "que es bueno", "lo mas popular",
            "algo rico", "sorprendeme", "no se que pedir", "que me sugieres", "dame una recomendacion")) {
            return "Te recomiendo:\n- Tacos al Pastor ($20.400 COP) - nuestro favorito\n- Arrachera ($46.800 COP) - jugosa y sabrosa\n- Guacamole con Totopos ($21.360 COP) - perfecto para empezar\n\n¿Te animas con alguno?";
        }

        String productPrice = resolveProductPriceQuery(m);
        if (productPrice != null) return productPrice;

        return null;
    }

    private String formatOrderSnapshot(OrderSnapshot snap) {
        StringBuilder sb = new StringBuilder("Tu pedido actual:\n");
        for (var item : snap.items()) {
            sb.append(String.format("  %dx %s - $%.2f\n", item.quantity(), item.productName(), item.lineTotal()));
        }
        sb.append(String.format("\nTotal: $%.2f", snap.total()));
        return sb.toString();
    }

    private String resolveProductPriceQuery(String msg) {
        if (!msg.contains("cuanto") && !msg.contains("precio") && !msg.contains("vale") && !msg.contains("cuesta")) return null;
        var menu = catalogService.getFullMenu();
        for (var cat : menu) {
            for (var p : cat.products()) {
                String clean = p.name().toLowerCase().replaceAll("\\(.*\\)", "").trim();
                String[] words = clean.split("\\s+");
                for (String w : words) {
                    if (w.length() > 3 && msg.contains(w)) {
                        return String.format("%s cuesta $%.2f COP", p.name(), p.price());
                    }
                }
            }
        }
        return null;
    }

    private boolean isOptionsRequest(String m) {
        return ConversationTextUtils.matchesAny(m,
            "dame opciones", "que opciones", "cuales opciones", "opciones de",
            "que tipos", "que tipo", "tipos de", "cuales hay", "cuales tienes",
            "que hay de", "muestrame opciones", "ver opciones");
    }

    private boolean isExplicitFullMenuRequest(String m) {
        return ConversationTextUtils.matchesAny(m,
            "dame el menu", "ver el menu", "ver menu", "quiero ver el menu",
            "el menu completo", "carta", "menu completo")
            || m.equals("menu") || m.equals("el menu");
    }

    private String resolveTopic(String current, java.util.List<ConversationTurn> history) {
        String fromCurrent = detectCategoryKeyword(current);
        if (fromCurrent != null) return fromCurrent;
        if (history == null) return null;
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationTurn turn = history.get(i);
            if (turn.getContent() == null) continue;
            String topic = detectCategoryKeyword(ConversationTextUtils.normalizeMessage(turn.getContent()));
            if (topic != null) return topic;
        }
        return null;
    }

    private String detectCategoryKeyword(String text) {
        if (text == null || text.isBlank()) return null;
        var menu = catalogService.getFullMenu();
        String best = null;
        int bestLen = 0;
        for (var cat : menu) {
            String catName = ConversationTextUtils.normalizeMessage(cat.name());
            if (catName.length() < 3) continue;
            String stem = catName.endsWith("s") ? catName.substring(0, catName.length() - 1) : catName;
            if ((text.contains(catName) || text.contains(stem)) && catName.length() > bestLen) {
                best = cat.name();
                bestLen = catName.length();
            }
        }
        if (text.contains("taco")) return best != null ? best : "Tacos";
        return best;
    }

    private String buildCategoryOptions(String categoryName) {
        var menu = catalogService.getFullMenu();
        for (var cat : menu) {
            boolean sameCategory = ConversationTextUtils.normalizeMessage(cat.name())
                .equals(ConversationTextUtils.normalizeMessage(categoryName));
            boolean tacoFallback = ConversationTextUtils.normalizeMessage(categoryName).contains("taco")
                && ConversationTextUtils.normalizeMessage(cat.name()).contains("taco");
            if (!sameCategory && !tacoFallback) continue;
            if (cat.products() == null || cat.products().isEmpty()) continue;
            StringBuilder sb = new StringBuilder("Tenemos estas opciones de ").append(cat.name()).append(":\n\n");
            for (var p : cat.products()) {
                if (!p.active()) continue;
                sb.append("- ").append(p.name()).append("\n");
            }
            sb.append("\nDime cual quieres y lo agrego al pedido.");
            return sb.toString();
        }
        return null;
    }

    private String buildMenuResponse() {
        var menu = catalogService.getFullMenu();
        StringBuilder sb = new StringBuilder("Aqui tienes nuestro menu:\n\n");
        for (var cat : menu) {
            if (cat.products().isEmpty()) continue;
            sb.append(cat.name()).append(":\n");
            for (var p : cat.products()) {
                String status = p.active() ? "" : " [NO DISPONIBLE]";
                sb.append(String.format("  - %s - $%.2f%s\n", p.name(), p.price(), status));
            }
            sb.append("\n");
        }
        sb.append("Dime que te gustaria pedir!");
        return sb.toString();
    }
}
