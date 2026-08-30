package com.restaurant.catalog;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "substitution_rules")
public class SubstitutionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "original_ingredient_id")
    private Ingredient originalIngredient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "substitute_ingredient_id")
    private Ingredient substituteIngredient;

    @Column(name = "price_diff")
    private BigDecimal priceDiff = BigDecimal.ZERO;

    private boolean active = true;

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public Ingredient getOriginalIngredient() { return originalIngredient; }
    public Ingredient getSubstituteIngredient() { return substituteIngredient; }
    public BigDecimal getPriceDiff() { return priceDiff; }
    public boolean isActive() { return active; }
}
