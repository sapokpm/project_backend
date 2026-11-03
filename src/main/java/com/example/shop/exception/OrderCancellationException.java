package com.example.shop.exception;

/** The order status does not allow cancelling. */
public class OrderCancellationException extends RuntimeException {
    public OrderCancellationException(String message) {
        super(message);
    }
}
