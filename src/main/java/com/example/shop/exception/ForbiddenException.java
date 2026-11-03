package com.example.shop.exception;

/** Valid token, wrong role or not your resource. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
