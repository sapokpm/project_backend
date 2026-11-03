package com.example.shop.exception;

/** A well-formed request that breaks a business rule (for example a wrong current password). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
