package com.example.shop.exception;

/** No token, or an invalid one. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
