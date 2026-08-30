package com.restaurant.catalog;

import com.restaurant.common.exception.ProductNotFoundException;
import com.restaurant.inventory.Inventory;
import com.restaurant.realtime.RealtimeService;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductIngredientRepository productIngredientRepository;
    private final com.restaurant.inventory.InventoryRepository inventoryRepository;
    private final RealtimeService realtimeService;
    private final ImageStorageService imageStorageService;

    public CatalogService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          IngredientRepository ingredientRepository,
                          ProductIngredientRepository productIngredientRepository,
                          com.restaurant.inventory.InventoryRepository inventoryRepository,
                          RealtimeService realtimeService,
                          ImageStorageService imageStorageService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.ingredientRepository = ingredientRepository;
        this.productIngredientRepository = productIngredientRepository;
        this.inventoryRepository = inventoryRepository;
        this.realtimeService = realtimeService;
        this.imageStorageService = imageStorageService;
    }

    public record ProductDTO(
        Long id, String name, String description, double price, String imageUrl, String categoryName,
        Long categoryId, boolean active, String[] allergens, List<IngredientDTO> ingredients,
        List<SubstitutionDTO> substitutions
    ) {}

    public record IngredientDTO(
        Long id, String name, String unit, String type, double extraPrice, String[] allergens
    ) {}

    public record IngredientWithStockDTO(
        Long id, String name, String unit, String type, double extraPrice, String[] allergens,
        double quantityAvailable, double minThreshold, boolean isAvailable, boolean isLow,
        double quantityRequired
    ) {}

    public record ProductWithStockDTO(
        Long id, String name, String description, double price, String imageUrl, String categoryName,
        Long categoryId, boolean active, String[] allergens,
        List<IngredientWithStockDTO> ingredients,
        List<SubstitutionDTO> substitutions
    ) {}

    public record CategoryWithStockDTO(Long id, String name, String description, int displayOrder, List<ProductWithStockDTO> products) {}

    public record SubstitutionDTO(
        Long id, Long originalIngredientId, String originalName,
        Long substituteIngredientId, String substituteName, double priceDiff
    ) {}

    public record CategoryDTO(Long id, String name, String description, int displayOrder, List<ProductDTO> products) {}

    public List<Category> getActiveCategories() {
        return categoryRepository.findByActiveTrueOrderByDisplayOrder();
    }

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAll();
    }

    @Transactional
    public String uploadProductImage(Long id, MultipartFile file) {
        String filename = imageStorageService.store(file);
        updateProductImage(id, filename);
        return filename;
    }

    public Resource loadProductImage(String filename) {
        return imageStorageService.load(filename);
    }

    public List<CategoryDTO> getFullMenu() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByDisplayOrder();
        return categories.stream().map(cat -> {
            List<ProductDTO> prods = productRepository.findByCategoryIdAndActiveTrue(cat.getId())
                .stream().map(this::toProductDTO).collect(Collectors.toList());
            return new CategoryDTO(cat.getId(), cat.getName(), cat.getDescription(), cat.getDisplayOrder(), prods);
        }).collect(Collectors.toList());
    }

    public List<CategoryWithStockDTO> getFullMenuWithStock() {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByDisplayOrder();
        return categories.stream().map(cat -> {
            List<ProductWithStockDTO> prods = productRepository.findByCategoryId(cat.getId())
                .stream().map(this::toProductWithStockDTO).collect(Collectors.toList());
            return new CategoryWithStockDTO(cat.getId(), cat.getName(), cat.getDescription(), cat.getDisplayOrder(), prods);
        }).collect(Collectors.toList());
    }

    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll().stream()
            .map(this::toProductDTO)
            .collect(Collectors.toList());
    }

    public ProductDTO getProductById(Long id) {
        Product p = productRepository.findByIdWithIngredients(id);
        if (p == null) throw new ProductNotFoundException(id);
        return toProductDTO(p);
    }

    @Transactional
    public void updateProductImage(Long id, String imageFilename) {
        Product p = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        p.setImageUrl("/api/catalog/images/" + imageFilename);
        productRepository.save(p);
    }

    public Product getProductEntity(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Genera el texto descriptivo de un producto para embeddings RAG.
     */
    public String buildProductTextForEmbedding(Product product) {
        StringBuilder sb = new StringBuilder();
        sb.append("Producto: ").append(product.getName()).append(". ");
        sb.append("Categoría: ").append(product.getCategory().getName()).append(". ");
        sb.append("Descripción: ").append(product.getDescription()).append(". ");
        sb.append("Precio: $").append(product.getPrice()).append(" MXN. ");

        if (product.getAllergens() != null && product.getAllergens().length > 0) {
            sb.append("Alérgenos: ").append(String.join(", ", product.getAllergens())).append(". ");
        }

        List<ProductIngredient> ingredients = product.getProductIngredients();
        if (ingredients != null && !ingredients.isEmpty()) {
            Map<String, List<String>> byType = new LinkedHashMap<>();
            for (ProductIngredient pi : ingredients) {
                byType.computeIfAbsent(pi.getIngredientType(), k -> new ArrayList<>())
                    .add(pi.getIngredient().getName());
            }
            byType.forEach((type, names) -> {
                String label = switch (type) {
                    case "BASE" -> "Ingredientes base";
                    case "REMOVABLE" -> "Ingredientes removibles (se pueden quitar)";
                    case "OPTIONAL" -> "Ingredientes opcionales (extras)";
                    default -> type;
                };
                sb.append(label).append(": ").append(String.join(", ", names)).append(". ");
            });
        }

        List<SubstitutionRule> subs = product.getSubstitutionRules();
        if (subs != null && !subs.isEmpty()) {
            sb.append("Sustituciones posibles: ");
            for (SubstitutionRule sr : subs) {
                sb.append(sr.getOriginalIngredient().getName())
                  .append(" → ").append(sr.getSubstituteIngredient().getName());
                if (sr.getPriceDiff().doubleValue() != 0) {
                    sb.append(" (").append(sr.getPriceDiff().doubleValue() > 0 ? "+" : "")
                      .append(sr.getPriceDiff()).append(")");
                }
                sb.append("; ");
            }
        }

        return sb.toString();
    }

    private ProductWithStockDTO toProductWithStockDTO(Product p) {
        List<IngredientWithStockDTO> ings = new ArrayList<>();
        if (p.getProductIngredients() != null) {
            for (ProductIngredient pi : p.getProductIngredients()) {
                var invOpt = inventoryRepository.findById(pi.getIngredient().getId());
                double qty = invOpt.map(i -> i.getQuantityAvailable().doubleValue()).orElse(0.0);
                double min = invOpt.map(i -> i.getMinThreshold().doubleValue()).orElse(0.0);
                boolean isAvail = qty > 0;
                boolean isLow = qty <= min && min > 0;
                ings.add(new IngredientWithStockDTO(
                    pi.getIngredient().getId(), pi.getIngredient().getName(),
                    pi.getIngredient().getUnit(), pi.getIngredientType(),
                    pi.getExtraPrice().doubleValue(), pi.getIngredient().getAllergens(),
                    qty, min, isAvail, isLow,
                    pi.getQuantityRequired() != null ? pi.getQuantityRequired().doubleValue() : 1
                ));
            }
        }

        List<SubstitutionDTO> subs = new ArrayList<>();
        if (p.getSubstitutionRules() != null) {
            for (SubstitutionRule sr : p.getSubstitutionRules()) {
                if (sr.isActive()) {
                    subs.add(new SubstitutionDTO(
                        sr.getId(), sr.getOriginalIngredient().getId(), sr.getOriginalIngredient().getName(),
                        sr.getSubstituteIngredient().getId(), sr.getSubstituteIngredient().getName(),
                        sr.getPriceDiff().doubleValue()
                    ));
                }
            }
        }

        return new ProductWithStockDTO(
            p.getId(), p.getName(), p.getDescription(), p.getPrice().doubleValue(), p.getImageUrl(),
            p.getCategory() != null ? p.getCategory().getName() : null,
            p.getCategory() != null ? p.getCategory().getId() : null,
            p.isActive(), p.getAllergens(), ings, subs
        );
    }

    private ProductDTO toProductDTO(Product p) {
        List<IngredientDTO> ings = new ArrayList<>();
        if (p.getProductIngredients() != null) {
            for (ProductIngredient pi : p.getProductIngredients()) {
                ings.add(new IngredientDTO(
                    pi.getIngredient().getId(), pi.getIngredient().getName(),
                    pi.getIngredient().getUnit(), pi.getIngredientType(),
                    pi.getExtraPrice().doubleValue(), pi.getIngredient().getAllergens()
                ));
            }
        }

        List<SubstitutionDTO> subs = new ArrayList<>();
        if (p.getSubstitutionRules() != null) {
            for (SubstitutionRule sr : p.getSubstitutionRules()) {
                if (sr.isActive()) {
                    subs.add(new SubstitutionDTO(
                        sr.getId(), sr.getOriginalIngredient().getId(), sr.getOriginalIngredient().getName(),
                        sr.getSubstituteIngredient().getId(), sr.getSubstituteIngredient().getName(),
                        sr.getPriceDiff().doubleValue()
                    ));
                }
            }
        }

        return new ProductDTO(
            p.getId(), p.getName(), p.getDescription(), p.getPrice().doubleValue(), p.getImageUrl(),
            p.getCategory() != null ? p.getCategory().getName() : null,
            p.getCategory() != null ? p.getCategory().getId() : null,
            p.isActive(), p.getAllergens(), ings, subs
        );
    }

    // ─── CRUD PRODUCTOS ────────────────────────────────────────────────────

    @Transactional
    public ProductDTO createProduct(String name, String description, BigDecimal price,
                                    Long categoryId, String[] allergens, List<IngredientRef> ingredientRefs) {
        Category cat;
        if (categoryId == null) {
            cat = categoryRepository.findByName("General")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName("General");
                    c.setDescription("Categoría por defecto");
                    c.setDisplayOrder(999);
                    c.setActive(true);
                    return categoryRepository.save(c);
                });
        } else {
            cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + categoryId));
        }
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setPrice(price);
        p.setCategory(cat);
        p.setAllergens(allergens != null ? allergens : new String[0]);
        p.setActive(true);
        p = productRepository.save(p);

        if (ingredientRefs != null) {
            for (IngredientRef ref : ingredientRefs) {
                Ingredient ing = ingredientRepository.findById(ref.ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado: " + ref.ingredientId));
                ProductIngredient pi = new ProductIngredient();
                pi.setProduct(p);
                pi.setIngredient(ing);
                pi.setIngredientType(ref.type);
                pi.setQuantityRequired(ref.quantityRequired != null ? ref.quantityRequired : BigDecimal.ONE);
                pi.setExtraPrice(ref.extraPrice != null ? ref.extraPrice : BigDecimal.ZERO);
                productIngredientRepository.save(pi);

                // Auto-create inventory entry if missing so the product is orderable immediately
                if ("BASE".equals(ref.type)) {
                    inventoryRepository.findById(ing.getId()).orElseGet(() -> {
                        Inventory inv = new Inventory();
                        inv.setIngredientId(ing.getId());
                        inv.setQuantityAvailable(BigDecimal.valueOf(100));
                        inv.setMinThreshold(BigDecimal.valueOf(10));
                        return inventoryRepository.save(inv);
                    });
                }
            }
        }

        broadcastMenuUpdate();
        return toProductDTO(productRepository.findByIdWithIngredients(p.getId()));
    }

    @Transactional
    public ProductDTO updateProduct(Long id, String name, String description, BigDecimal price,
                                    Long categoryId, String[] allergens, Boolean active) {
        Product p = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        if (name != null) p.setName(name);
        if (description != null) p.setDescription(description);
        if (price != null) p.setPrice(price);
        if (categoryId != null) {
            Category cat = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + categoryId));
            p.setCategory(cat);
        }
        if (allergens != null) p.setAllergens(allergens);
        if (active != null) p.setActive(active);
        productRepository.save(p);
        broadcastMenuUpdate();
        return toProductDTO(productRepository.findByIdWithIngredients(p.getId()));
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product p = productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
        productIngredientRepository.deleteByProductIdAndIngredientId(id, null);
        // soft delete: disable instead of hard delete
        p.setActive(false);
        productRepository.save(p);
        broadcastMenuUpdate();
    }

    // ─── CRUD CATEGORÍAS ───────────────────────────────────────────────────

    @Transactional
    public Category createCategory(String name, String description, int displayOrder) {
        Category c = new Category();
        c.setName(name);
        c.setDescription(description);
        c.setDisplayOrder(displayOrder);
        c.setActive(true);
        Category saved = categoryRepository.save(c);
        broadcastMenuUpdate();
        return saved;
    }

    @Transactional
    public Category updateCategory(Long id, String name, String description, Integer displayOrder, Boolean active) {
        Category c = categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + id));
        if (name != null) c.setName(name);
        if (description != null) c.setDescription(description);
        if (displayOrder != null) c.setDisplayOrder(displayOrder);
        if (active != null) c.setActive(active);
        Category saved = categoryRepository.save(c);
        broadcastMenuUpdate();
        return saved;
    }

    // ─── CRUD INGREDIENTES ─────────────────────────────────────────────────

    @Transactional
    public Ingredient createIngredient(String name, String unit, String[] allergens) {
        Ingredient ing = new Ingredient();
        ing.setName(name);
        ing.setUnit(unit);
        ing.setAllergens(allergens != null ? allergens : new String[0]);
        ing.setActive(true);
        Ingredient saved = ingredientRepository.save(ing);

        // Auto-create inventory entry so the ingredient is stock-tracked immediately
        Inventory inv = new Inventory();
        inv.setIngredientId(saved.getId());
        inv.setQuantityAvailable(BigDecimal.valueOf(100));
        inv.setMinThreshold(BigDecimal.valueOf(10));
        inventoryRepository.save(inv);

        broadcastMenuUpdate();
        return saved;
    }

    @Transactional
    public Ingredient updateIngredient(Long id, String name, String unit, String[] allergens, Boolean active) {
        Ingredient ing = ingredientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado: " + id));
        if (name != null) ing.setName(name);
        if (unit != null) ing.setUnit(unit);
        if (allergens != null) ing.setAllergens(allergens);
        if (active != null) ing.setActive(active);
        Ingredient saved = ingredientRepository.save(ing);
        broadcastMenuUpdate();
        return saved;
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category c = categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + id));
        c.setActive(false);
        categoryRepository.save(c);
        broadcastMenuUpdate();
    }

    @Transactional
    public void deleteIngredient(Long id) {
        Ingredient ing = ingredientRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado: " + id));
        ing.setActive(false);
        ingredientRepository.save(ing);
        broadcastMenuUpdate();
    }

    // ─── BROADCAST MENU UPDATE ─────────────────────────────────────────────

    public void broadcastMenuUpdate() {
        realtimeService.notifyMenuUpdate();
    }

    public record IngredientRef(Long ingredientId, String type, BigDecimal quantityRequired, BigDecimal extraPrice) {}
}
