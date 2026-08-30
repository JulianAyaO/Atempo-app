package com.restaurant.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "ingredients")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String unit;

    @Column(columnDefinition = "text[]")
    private String[] allergens = {};

    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String[] getAllergens() { return allergens; }
    public void setAllergens(String[] allergens) { this.allergens = allergens; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
