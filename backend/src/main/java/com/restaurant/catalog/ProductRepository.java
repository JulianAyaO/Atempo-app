package com.restaurant.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrue();

    @Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.active = true ORDER BY p.category.displayOrder, p.name")
    List<Product> findAllActiveWithCategory();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.productIngredients pi LEFT JOIN FETCH pi.ingredient WHERE p.id = :id")
    Product findByIdWithIngredients(Long id);

    List<Product> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Product> findByCategoryId(Long categoryId);
}
