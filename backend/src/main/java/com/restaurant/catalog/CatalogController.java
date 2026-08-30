package com.restaurant.catalog;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(catalogService.getActiveCategories());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> createCategory(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.getOrDefault("description", "");
        int displayOrder = body.get("displayOrder") != null ? ((Number) body.get("displayOrder")).intValue() : 0;
        return ResponseEntity.ok(catalogService.createCategory(name, description, displayOrder));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Category> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.containsKey("name") ? (String) body.get("name") : null;
        String description = body.containsKey("description") ? (String) body.get("description") : null;
        Integer displayOrder = body.containsKey("displayOrder") ? ((Number) body.get("displayOrder")).intValue() : null;
        Boolean active = body.containsKey("active") ? (Boolean) body.get("active") : null;
        return ResponseEntity.ok(catalogService.updateCategory(id, name, description, displayOrder, active));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Categoria desactivada"));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<CatalogService.CategoryDTO>> getMenu() {
        return ResponseEntity.ok(catalogService.getFullMenu());
    }

    @GetMapping("/menu-with-stock")
    public ResponseEntity<List<CatalogService.CategoryWithStockDTO>> getMenuWithStock() {
        return ResponseEntity.ok(catalogService.getFullMenuWithStock());
    }

    @GetMapping("/products")
    public ResponseEntity<List<CatalogService.ProductDTO>> getProducts() {
        return ResponseEntity.ok(catalogService.getAllProducts());
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<CatalogService.ProductDTO> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getProductById(id));
    }

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CatalogService.ProductDTO> createProduct(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String description = (String) body.getOrDefault("description", "");
        BigDecimal price = body.get("price") != null ? new BigDecimal(body.get("price").toString()) : BigDecimal.ZERO;
        Long categoryId = body.get("categoryId") != null ? ((Number) body.get("categoryId")).longValue() : null;
        String[] allergens = body.containsKey("allergens") ? ((List<?>) body.get("allergens")).stream().map(Object::toString).toArray(String[]::new) : new String[0];
        List<CatalogService.IngredientRef> refs = new java.util.ArrayList<>();
        if (body.containsKey("ingredients")) {
            List<Map<String, Object>> ings = (List<Map<String, Object>>) body.get("ingredients");
            for (Map<String, Object> i : ings) {
                Long ingId = ((Number) i.get("ingredientId")).longValue();
                String type = (String) i.getOrDefault("type", "BASE");
                BigDecimal qty = i.containsKey("quantityRequired") ? new BigDecimal(i.get("quantityRequired").toString()) : BigDecimal.ONE;
                BigDecimal extra = i.containsKey("extraPrice") ? new BigDecimal(i.get("extraPrice").toString()) : BigDecimal.ZERO;
                refs.add(new CatalogService.IngredientRef(ingId, type, qty, extra));
            }
        }
        return ResponseEntity.ok(catalogService.createProduct(name, description, price, categoryId, allergens, refs));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CatalogService.ProductDTO> updateProduct(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.containsKey("name") ? (String) body.get("name") : null;
        String description = body.containsKey("description") ? (String) body.get("description") : null;
        BigDecimal price = body.containsKey("price") ? new BigDecimal(body.get("price").toString()) : null;
        Long categoryId = body.containsKey("categoryId") ? ((Number) body.get("categoryId")).longValue() : null;
        String[] allergens = body.containsKey("allergens") ? ((List<?>) body.get("allergens")).stream().map(Object::toString).toArray(String[]::new) : null;
        Boolean active = body.containsKey("active") ? (Boolean) body.get("active") : null;
        return ResponseEntity.ok(catalogService.updateProduct(id, name, description, price, categoryId, allergens, active));
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable Long id) {
        catalogService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Producto desactivado"));
    }

    @GetMapping("/ingredients")
    public ResponseEntity<List<Ingredient>> getIngredients() {
        return ResponseEntity.ok(catalogService.getAllIngredients());
    }

    @PostMapping("/ingredients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Ingredient> createIngredient(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String unit = (String) body.getOrDefault("unit", "unidad");
        String[] allergens = body.containsKey("allergens") ? ((List<?>) body.get("allergens")).stream().map(Object::toString).toArray(String[]::new) : new String[0];
        return ResponseEntity.ok(catalogService.createIngredient(name, unit, allergens));
    }

    @PutMapping("/ingredients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Ingredient> updateIngredient(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = body.containsKey("name") ? (String) body.get("name") : null;
        String unit = body.containsKey("unit") ? (String) body.get("unit") : null;
        String[] allergens = body.containsKey("allergens") ? ((List<?>) body.get("allergens")).stream().map(Object::toString).toArray(String[]::new) : null;
        Boolean active = body.containsKey("active") ? (Boolean) body.get("active") : null;
        return ResponseEntity.ok(catalogService.updateIngredient(id, name, unit, allergens, active));
    }

    @DeleteMapping("/ingredients/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteIngredient(@PathVariable Long id) {
        catalogService.deleteIngredient(id);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Ingrediente desactivado"));
    }

    @PostMapping("/products/{id}/image")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> uploadProductImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(catalogService.uploadProductImage(id, file));
    }

    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
        Resource resource = catalogService.loadProductImage(filename);
        String contentType = filename.endsWith(".png") ? "image/png" :
            filename.endsWith(".gif") ? "image/gif" : "image/jpeg";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(resource);
    }
}
