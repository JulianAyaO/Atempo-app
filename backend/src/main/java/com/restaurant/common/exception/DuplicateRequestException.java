package com.restaurant.common.exception;

public class DuplicateRequestException extends RuntimeException {
    public DuplicateRequestException(String msg) { super(msg); }
}
