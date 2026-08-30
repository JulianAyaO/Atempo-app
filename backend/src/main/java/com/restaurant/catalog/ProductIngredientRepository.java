package com.restaurant.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {
    List<ProductIngredient> findByProductId(Long productId);
    List<ProductIngredient> findByIngredientId(Long ingredientId);
    void deleteByProductIdAndIngredientId(Long productId, Long ingredientId);
}
