package com.example.shop.exception;

/** Not enough stock to reserve. */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
