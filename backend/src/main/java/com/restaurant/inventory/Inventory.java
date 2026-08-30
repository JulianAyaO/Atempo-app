package com.restaurant.inventory;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @Column(name = "ingredient_id")
    private Long ingredientId;

    @Column(name = "quantity_available")
    private BigDecimal quantityAvailable;

    @Column(name = "min_threshold")
    private BigDecimal minThreshold;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private int version;

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
    public BigDecimal getQuantityAvailable() { return quantityAvailable; }
    public void setQuantityAvailable(BigDecimal quantityAvailable) { this.quantityAvailable = quantityAvailable; }
    public BigDecimal getMinThreshold() { return minThreshold; }
    public void setMinThreshold(BigDecimal minThreshold) { this.minThreshold = minThreshold; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public int getVersion() { return version; }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
