package com.example.shop.exception;

/** Expired, used up, or not applicable. */
public class PromoCodeInvalidException extends RuntimeException {
    public PromoCodeInvalidException(String message) {
        super(message);
    }
}
