package com.restaurant.conversation;

import com.restaurant.catalog.CatalogService;
import com.restaurant.common.dto.OrderSnapshot;
import com.restaurant.common.exception.InsufficientInventoryException;
import com.restaurant.inventory.InventoryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class CatalogContextBuilder {

    private final CatalogService catalogService;
    private final InventoryService inventoryService;
    private final CatalogRAGService ragService;

    public CatalogContextBuilder(CatalogService catalogService, InventoryService inventoryService, CatalogRAGService ragService) {
        this.catalogService = catalogService;
        this.inventoryService = inventoryService;
        this.ragService = ragService;
    }

    public String buildCatalogContext(String userMessage) {
        var menu = catalogService.getFullMenu();
        var inventoryMap = new HashMap<Long, Double>();
        try {
            for (var inv : inventoryService.getFullInventory()) {
                inventoryMap.put(inv.ingredientId(), inv.quantityAvailable());
            }
        } catch (Exception ignored) {}

        var sb = new StringBuilder();
        appendRelevantProducts(sb, userMessage);
        sb.append("\nMENU COMPLETO DISPONIBLE:\n");
        for (var cat : menu) {
            sb.append("\n[").append(cat.name()).append("] - ").append(cat.description() != null ? cat.description() : "").append("\n");
            for (var p : cat.products()) {
                String avail;
                try {
                    inventoryService.validateAvailability(p.id(), 1);
                    avail = "";
                } catch (InsufficientInventoryException e) {
                    avail = " (NO DISPONIBLE - " + e.getMessage() + ")";
                } catch (Exception e) {
                    avail = "";
                }
                sb.append(String.format("  ID:%d %s - %s - $%.2f%s", p.id(), p.name(),
                    p.description() != null ? p.description() : "", p.price(), avail));
                List<String> removable = new ArrayList<>();
                List<String> optional = new ArrayList<>();
                List<String> allergens = new ArrayList<>();
                List<String> unavailableOptional = new ArrayList<>();
                for (var ing : p.ingredients()) {
                    if ("REMOVABLE".equals(ing.type())) removable.add(ing.name());
                    if ("OPTIONAL".equals(ing.type())) {
                        Double qty = inventoryMap.get(ing.id());
                        if (qty != null && qty <= 0) {
                            unavailableOptional.add(ing.name());
                        } else {
                            optional.add(ing.name() + (ing.extraPrice() > 0 ? " +$" + ing.extraPrice() : ""));
                        }
                    }
                }
                if (p.allergens() != null && p.allergens().length > 0) {
                    allergens.addAll(List.of(p.allergens()));
                }
                if (!removable.isEmpty()) sb.append(" [quitar: ").append(String.join(",", removable)).append("]");
                if (!optional.isEmpty()) sb.append(" [extras: ").append(String.join(",", optional)).append("]");
                if (!unavailableOptional.isEmpty()) sb.append(" [extras agotados: ").append(String.join(",", unavailableOptional)).append("]");
                if (!allergens.isEmpty()) sb.append(" [alerg: ").append(String.join(",", allergens)).append("]");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private void appendRelevantProducts(StringBuilder sb, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return;
        try {
            List<CatalogRAGService.RAGResult> results = ragService.search(userMessage);
            if (results.isEmpty()) return;
            sb.append("PRODUCTOS RELEVANTES POR BUSQUEDA SEMANTICA:\n");
            for (CatalogRAGService.RAGResult result : results) {
                sb.append("- ID:").append(result.entityId())
                    .append(" score=").append(String.format("%.2f", result.similarity()))
                    .append(" ").append(result.content()).append("\n");
            }
        } catch (Exception e) {
            // Si pgvector/Ollama no está disponible, el catálogo completo mantiene el chat funcional.
        }
    }

    public String formatOrderState(OrderSnapshot os) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estado: ").append(os.status()).append("\nItems:\n");
        for (var item : os.items()) {
            sb.append("- ").append(item.quantity()).append("x ").append(item.productName()).append("\n");
        }
        sb.append("Total: ").append(os.total()).append(" (no mostrar precios al cliente)");
        return sb.toString();
    }
}
