package com.restaurant.common.exception;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String msg) { super(msg); }
}
