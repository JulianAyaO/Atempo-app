package com.restaurant.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.catalog.*;
import com.restaurant.common.event.InventoryLowEvent;
import com.restaurant.common.exception.InsufficientInventoryException;
import com.restaurant.common.exception.ProductNotFoundException;
import com.restaurant.realtime.RealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final InventoryMovementRepository movementRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final RealtimeService realtimeService;
    private final ObjectMapper objectMapper;

    public InventoryService(InventoryRepository inventoryRepository,
                            ProductRepository productRepository,
                            IngredientRepository ingredientRepository,
                            ProductIngredientRepository productIngredientRepository,
                            InventoryMovementRepository movementRepository,
                            org.springframework.context.ApplicationEventPublisher eventPublisher,
                            RealtimeService realtimeService,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.ingredientRepository = ingredientRepository;
        this.productIngredientRepository = productIngredientRepository;
        this.movementRepository = movementRepository;
        this.eventPublisher = eventPublisher;
        this.realtimeService = realtimeService;
        this.objectMapper = objectMapper;
    }

    public record InventoryItemDTO(
        Long ingredientId, String ingredientName, String unit,
        double quantityAvailable, double minThreshold, boolean isLow
    ) {}

    public record MovementDTO(Long id, String ingredientName, String type,
                              double delta, double stockBefore, double stockAfter,
                              String referenceId, String createdAt) {}

    // ──────────────────────────────────────────────────────────────────────────
    // VALIDACIÓN DE DISPONIBILIDAD
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Valida que hay suficiente inventario de todos los ingredientes BASE de un producto.
     */
    public void validateAvailability(Long productId, int quantity) {
        validateAvailability(productId, quantity, "[]");
    }

    public void validateAvailability(Long productId, int quantity, String modifiersJson) {
        Product product = productRepository.findByIdWithIngredients(productId);
        if (product == null) throw new ProductNotFoundException(productId);
        if (!product.isActive()) throw new InsufficientInventoryException("El producto '" + product.getName() + "' no está disponible");

        List<String> unavailable = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : buildIngredientConsumption(product, quantity, modifiersJson).entrySet()) {
            Inventory inv = inventoryRepository.findById(entry.getKey()).orElse(null);
            BigDecimal needed = entry.getValue();
            Ingredient ingredient = ingredientRepository.findById(entry.getKey()).orElse(null);
            String ingredientName = ingredient != null ? ingredient.getName() : "Ingrediente " + entry.getKey();
            String unit = ingredient != null ? ingredient.getUnit() : "";

            if (inv == null || inv.getQuantityAvailable().compareTo(needed) < 0) {
                double available = inv != null ? inv.getQuantityAvailable().doubleValue() : 0;
                unavailable.add(String.format("%s (necesario: %.1f %s, disponible: %.1f)",
                    ingredientName, needed.doubleValue(), unit, available));
            }
        }

        if (!unavailable.isEmpty()) {
            throw new InsufficientInventoryException(
                "Inventario insuficiente para '" + product.getName() + "': " + String.join(", ", unavailable));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CONSUMO DE INVENTARIO (SALIDA) — con SELECT FOR UPDATE
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Descuenta el inventario para un producto × cantidad.
     * Agrupa consumos por ingrediente y usa SELECT FOR UPDATE (pessimistic locking).
     * Registra movimientos de tipo SALIDA.
     */
    @Transactional
    public void deductStock(Long productId, int quantity, String referenceId) {
        deductStock(productId, quantity, referenceId, "[]");
    }

    @Transactional
    public void deductStock(Long productId, int quantity, String referenceId, String modifiersJson) {
        Product product = productRepository.findByIdWithIngredients(productId);
        if (product == null) return;

        Map<Long, BigDecimal> consumptions = buildIngredientConsumption(product, quantity, modifiersJson);

        for (Map.Entry<Long, BigDecimal> entry : consumptions.entrySet()) {
            Long ingredientId = entry.getKey();
            BigDecimal delta = entry.getValue();

            Inventory inv = inventoryRepository.findByIdForUpdate(ingredientId)
                .orElseThrow(() -> new InsufficientInventoryException(
                    "Inventario no encontrado para ingrediente: " + ingredientId));

            if (inv.getQuantityAvailable().compareTo(delta) < 0) {
                throw new InsufficientInventoryException(
                    "Stock insuficiente para ingrediente " + ingredientId +
                    " (disponible: " + inv.getQuantityAvailable() + ", necesario: " + delta + ")");
            }

            BigDecimal before = inv.getQuantityAvailable();
            inv.setQuantityAvailable(inv.getQuantityAvailable().subtract(delta));
            inventoryRepository.save(inv);

            recordMovement(ingredientId, InventoryMovement.MovementType.SALIDA,
                delta, before, inv.getQuantityAvailable(), referenceId);

            checkLowStockAndNotify(inv, ingredientId);
        }

        // Re-evaluar disponibilidad del producto consumido
        recalculateProductAvailability(productId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REVERSIÓN DE STOCK (ENTRADA) — al cancelar pedido
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve el stock consumido por un pedido. Registra movimientos ENTRADA.
     * Llámalo cuando un pedido pasa a CANCELADO.
     */
    @Transactional
    public void revertStockForOrder(Long productId, int quantity, String orderReference) {
        revertStockForOrder(productId, quantity, orderReference, "[]");
    }

    @Transactional
    public void revertStockForOrder(Long productId, int quantity, String orderReference, String modifiersJson) {
        Product product = productRepository.findByIdWithIngredients(productId);
        if (product == null) return;

        Map<Long, BigDecimal> returns = buildIngredientConsumption(product, quantity, modifiersJson);

        for (Map.Entry<Long, BigDecimal> entry : returns.entrySet()) {
            Long ingredientId = entry.getKey();
            BigDecimal delta = entry.getValue();

            Inventory inv = inventoryRepository.findByIdForUpdate(ingredientId)
                .orElseThrow(() -> new RuntimeException("Inventario no encontrado: " + ingredientId));

            BigDecimal before = inv.getQuantityAvailable();
            inv.setQuantityAvailable(inv.getQuantityAvailable().add(delta));
            inventoryRepository.save(inv);

            recordMovement(ingredientId, InventoryMovement.MovementType.ENTRADA,
                delta, before, inv.getQuantityAvailable(), orderReference);

            checkLowStockAndNotify(inv, ingredientId);
        }

        // Re-evaluar: al devolver stock, los productos podrían volver a estar disponibles
        recalculateProductAvailability(productId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REABASTECIMIENTO
    // ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public void restock(Long ingredientId, double quantity) {
        Inventory inv = inventoryRepository.findByIdForUpdate(ingredientId)
            .orElseThrow(() -> new RuntimeException("Inventario no encontrado: " + ingredientId));

        BigDecimal before = inv.getQuantityAvailable();
        BigDecimal delta = BigDecimal.valueOf(quantity);
        inv.setQuantityAvailable(inv.getQuantityAvailable().add(delta));
        inventoryRepository.save(inv);

        recordMovement(ingredientId, InventoryMovement.MovementType.ENTRADA,
            delta, before, inv.getQuantityAvailable(), "RESTOCK");

        log.info("Restock ingrediente {}: +{}", ingredientId, quantity);

        // Re-evaluar todos los productos que usan este ingrediente como BASE
        recalculateProductsForIngredient(ingredientId);
        realtimeService.notifyInventoryUpdated();
    }

    @Transactional
    public void setStock(Long ingredientId, double quantity) {
        Inventory inv = inventoryRepository.findByIdForUpdate(ingredientId)
            .orElseThrow(() -> new RuntimeException("Inventario no encontrado: " + ingredientId));

        BigDecimal before = inv.getQuantityAvailable();
        BigDecimal newQty = BigDecimal.valueOf(quantity);
        inv.setQuantityAvailable(newQty);
        inventoryRepository.save(inv);

        recordMovement(ingredientId, InventoryMovement.MovementType.AJUSTE,
            newQty.subtract(before).abs(), before, newQty, "MANUAL");

        log.info("Stock ingrediente {} establecido a: {}", ingredientId, quantity);

        if (quantity <= 0) {
            String name = getIngredientName(ingredientId);
            eventPublisher.publishEvent(new InventoryLowEvent(this, name, quantity,
                getIngredientUnit(ingredientId)));
        }

        recalculateProductsForIngredient(ingredientId);
        realtimeService.notifyInventoryUpdated();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DISPONIBILIDAD AUTOMÁTICA DE PRODUCTOS
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Un producto está disponible SOLO si todos sus ingredientes BASE tienen stock > 0.
     */
    @Transactional
    public void recalculateProductAvailability(Long productId) {
        Product product = productRepository.findByIdWithIngredients(productId);
        if (product == null) return;

        boolean available = true;
        for (ProductIngredient pi : product.getProductIngredients()) {
            if (!"BASE".equals(pi.getIngredientType())) continue;
            Inventory inv = inventoryRepository.findById(pi.getIngredient().getId()).orElse(null);
            if (inv == null || inv.getQuantityAvailable().compareTo(BigDecimal.ZERO) <= 0) {
                available = false;
                break;
            }
        }

        if (product.isActive() != available) {
            product.setActive(available);
            productRepository.save(product);
            log.info("Producto '{}' disponibilidad actualizada: {} → {}",
                product.getName(), !available, available);
            // Notificar a todos los clientes que el menú cambió
            realtimeService.broadcastMenuUpdate(product.getId(), product.getName(), available);
        }
    }

    /**
     * Re-evalúa TODOS los productos que usan un ingrediente como BASE.
     */
    @Transactional
    public void recalculateProductsForIngredient(Long ingredientId) {
        List<ProductIngredient> usages = productIngredientRepository.findByIngredientId(ingredientId);
        for (ProductIngredient pi : usages) {
            if (!"BASE".equals(pi.getIngredientType())) continue;
            recalculateProductAvailability(pi.getProduct().getId());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MOVIMIENTOS DE INVENTARIO
    // ──────────────────────────────────────────────────────────────────────────

    private void recordMovement(Long ingredientId, InventoryMovement.MovementType type,
                                 BigDecimal delta, BigDecimal before, BigDecimal after, String ref) {
        InventoryMovement m = new InventoryMovement();
        m.setIngredientId(ingredientId);
        m.setIngredientName(getIngredientName(ingredientId));
        m.setMovementType(type);
        m.setQuantityDelta(delta);
        m.setStockBefore(before);
        m.setStockAfter(after);
        m.setReferenceId(ref);
        movementRepository.save(m);
    }

    private Map<Long, BigDecimal> buildIngredientConsumption(Product product, int quantity, String modifiersJson) {
        ModifierSelection modifiers = parseModifiers(modifiersJson);
        Map<Long, BigDecimal> consumptions = new LinkedHashMap<>();

        for (ProductIngredient pi : product.getProductIngredients()) {
            Long ingredientId = pi.getIngredient().getId();
            String ingredientName = pi.getIngredient().getName();
            String type = pi.getIngredientType();
            boolean removed = modifiers.matchesRemoved(ingredientId, ingredientName);
            boolean added = modifiers.matchesAdded(ingredientId, ingredientName);

            boolean shouldConsume = ("BASE".equals(type) || "REMOVABLE".equals(type)) && !removed;
            if ("OPTIONAL".equals(type) && added) {
                shouldConsume = true;
            }
            if (!shouldConsume) continue;

            BigDecimal delta = pi.getQuantityRequired().multiply(BigDecimal.valueOf(quantity));
            consumptions.merge(ingredientId, delta, BigDecimal::add);
        }

        return consumptions;
    }

    private ModifierSelection parseModifiers(String modifiersJson) {
        if (modifiersJson == null || modifiersJson.isBlank() || "[]".equals(modifiersJson)) {
            return new ModifierSelection(Set.of(), Set.of(), Set.of(), Set.of());
        }
        Set<Long> removedIds = new HashSet<>();
        Set<String> removedNames = new HashSet<>();
        Set<Long> addedIds = new HashSet<>();
        Set<String> addedNames = new HashSet<>();
        try {
            List<Map<String, Object>> modifiers = objectMapper.readValue(modifiersJson, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> modifier : modifiers) {
                String type = String.valueOf(modifier.getOrDefault("type", "")).toUpperCase(Locale.ROOT);
                Object idObj = modifier.get("ingredientId");
                Long ingredientId = idObj instanceof Number n ? n.longValue() : null;
                String ingredientName = normalizeName(String.valueOf(modifier.getOrDefault("ingredientName", "")));
                if ("REMOVE".equals(type)) {
                    if (ingredientId != null) removedIds.add(ingredientId);
                    if (!ingredientName.isBlank()) removedNames.add(ingredientName);
                }
                if ("ADD".equals(type) || "SUBSTITUTE".equals(type)) {
                    if (ingredientId != null) addedIds.add(ingredientId);
                    if (!ingredientName.isBlank()) addedNames.add(ingredientName);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudieron interpretar modificadores de inventario: {}", modifiersJson);
        }
        return new ModifierSelection(removedIds, removedNames, addedIds, addedNames);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record ModifierSelection(Set<Long> removedIds, Set<String> removedNames, Set<Long> addedIds, Set<String> addedNames) {
        boolean matchesRemoved(Long id, String name) {
            return removedIds.contains(id) || removedNames.contains(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        }

        boolean matchesAdded(Long id, String name) {
            return addedIds.contains(id) || addedNames.contains(name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
        }
    }

    private void checkLowStockAndNotify(Inventory inv, Long ingredientId) {
        if (inv.getQuantityAvailable().compareTo(inv.getMinThreshold()) <= 0) {
            String name = getIngredientName(ingredientId);
            String unit = getIngredientUnit(ingredientId);
            log.warn("⚠ STOCK BAJO: {} - disponible: {} {}", name, inv.getQuantityAvailable(), unit);
            eventPublisher.publishEvent(new InventoryLowEvent(this, name,
                inv.getQuantityAvailable().doubleValue(), unit));
            realtimeService.notifyInventoryAlert(name, inv.getQuantityAvailable().doubleValue(), unit);
        }
    }

    private String getIngredientName(Long id) {
        return ingredientRepository.findById(id).map(Ingredient::getName).orElse("ID:" + id);
    }

    private String getIngredientUnit(Long id) {
        return ingredientRepository.findById(id).map(Ingredient::getUnit).orElse("");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ──────────────────────────────────────────────────────────────────────────

    public List<InventoryItemDTO> getFullInventory() {
        return inventoryRepository.findAll().stream().map(inv -> {
            var ingredient = ingredientRepository.findById(inv.getIngredientId());
            String name = ingredient.map(Ingredient::getName).orElse("Desconocido");
            String unit = ingredient.map(Ingredient::getUnit).orElse("");
            boolean isLow = inv.getQuantityAvailable().compareTo(inv.getMinThreshold()) <= 0;
            return new InventoryItemDTO(inv.getIngredientId(), name, unit,
                inv.getQuantityAvailable().doubleValue(), inv.getMinThreshold().doubleValue(), isLow);
        }).collect(Collectors.toList());
    }

    public List<InventoryItemDTO> getLowStockAlerts() {
        return getFullInventory().stream().filter(InventoryItemDTO::isLow).collect(Collectors.toList());
    }

    public List<MovementDTO> getMovements(Long ingredientId) {
        return movementRepository.findByIngredientIdOrderByCreatedAtDesc(ingredientId).stream().map(m ->
            new MovementDTO(m.getId(), m.getIngredientName(), m.getMovementType().name(),
                m.getQuantityDelta().doubleValue(), m.getStockBefore().doubleValue(),
                m.getStockAfter().doubleValue(), m.getReferenceId(), m.getCreatedAt().toString())
        ).collect(Collectors.toList());
    }

    public record AvailabilityCheck(boolean available, String message, List<String> alternatives) {}

    public AvailabilityCheck checkAndSuggestAlternatives(Long productId) {
        try {
            validateAvailability(productId, 1);
            return new AvailabilityCheck(true, "Disponible", List.of());
        } catch (InsufficientInventoryException e) {
            Product product = productRepository.findByIdWithIngredients(productId);
            List<String> alternatives = new ArrayList<>();
            if (product != null && product.getSubstitutionRules() != null) {
                for (SubstitutionRule sr : product.getSubstitutionRules()) {
                    Inventory subInv = inventoryRepository.findById(sr.getSubstituteIngredient().getId()).orElse(null);
                    if (subInv != null && subInv.getQuantityAvailable().compareTo(BigDecimal.ONE) > 0) {
                        alternatives.add("Sustituir " + sr.getOriginalIngredient().getName() +
                            " por " + sr.getSubstituteIngredient().getName());
                    }
                }
            }
            return new AvailabilityCheck(false, e.getMessage(), alternatives);
        }
    }
}
