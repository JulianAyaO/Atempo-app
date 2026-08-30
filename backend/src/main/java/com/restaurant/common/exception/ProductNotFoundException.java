package com.restaurant.common.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(Long id) {
        super("Producto no encontrado con ID: " + id);
    }
    public ProductNotFoundException(String msg) {
        super(msg);
    }
}
