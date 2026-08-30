package com.restaurant.common.exception;

public class ConcurrencyConflictException extends RuntimeException {
    public ConcurrencyConflictException(String msg) { super(msg); }
}
