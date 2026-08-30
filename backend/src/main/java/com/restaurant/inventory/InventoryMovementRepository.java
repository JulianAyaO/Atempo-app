package com.restaurant.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    List<InventoryMovement> findByIngredientIdOrderByCreatedAtDesc(Long ingredientId);

    List<InventoryMovement> findByReferenceIdOrderByCreatedAtDesc(String referenceId);

    @Query("SELECT m FROM InventoryMovement m ORDER BY m.createdAt DESC")
    List<InventoryMovement> findRecentMovements();
}
