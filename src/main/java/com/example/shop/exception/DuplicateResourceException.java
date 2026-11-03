package com.example.shop.exception;

/** Creating something that must be unique and already exists (sku, slug, code). */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
