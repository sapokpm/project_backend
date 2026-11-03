package com.example.shop.exception;

/** Refusing to delete something other rows still depend on. */
public class ResourceInUseException extends RuntimeException {
    public ResourceInUseException(String message) {
        super(message);
    }
}
