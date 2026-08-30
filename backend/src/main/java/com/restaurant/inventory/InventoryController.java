package com.restaurant.inventory;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasAnyRole('KITCHEN','ADMIN')")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<InventoryService.InventoryItemDTO>> getInventory() {
        return ResponseEntity.ok(inventoryService.getFullInventory());
    }

    @GetMapping("/alerts")
    public ResponseEntity<List<InventoryService.InventoryItemDTO>> getAlerts() {
        return ResponseEntity.ok(inventoryService.getLowStockAlerts());
    }

    @PatchMapping("/{ingredientId}/restock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> restock(
            @PathVariable Long ingredientId, @RequestBody Map<String, Double> body) {
        double qty = body.getOrDefault("quantity", 0.0);
        inventoryService.restock(ingredientId, qty);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Reabastecido: +" + qty));
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<InventoryService.AvailabilityCheck> checkAvailability(@PathVariable Long productId) {
        return ResponseEntity.ok(inventoryService.checkAndSuggestAlternatives(productId));
    }

    @PatchMapping("/{ingredientId}/set-unavailable")
    public ResponseEntity<Map<String, String>> markUnavailable(@PathVariable Long ingredientId) {
        inventoryService.setStock(ingredientId, 0);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Ingrediente marcado como no disponible"));
    }

    @PatchMapping("/{ingredientId}/set-stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> setStock(
            @PathVariable Long ingredientId, @RequestBody Map<String, Double> body) {
        double qty = body.getOrDefault("quantity", 0.0);
        inventoryService.setStock(ingredientId, qty);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Stock actualizado a: " + qty));
    }
}
