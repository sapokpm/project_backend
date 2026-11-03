package com.example.shop.exception;

/** The payment simulation declined. */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
}
