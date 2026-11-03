package com.example.shop.exception;

/** The requested order status is not reachable from the current one. */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
