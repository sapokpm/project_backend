package com.example.shop.service;

import com.example.shop.dto.request.ProcessPaymentRequest;
import com.example.shop.dto.response.PaymentMethodResponse;
import com.example.shop.dto.response.PaymentResponse;
import com.example.shop.dto.response.PaymentStatusResponse;

import java.util.List;

public interface PaymentService {
    /**
     * Pays the caller's own order. The amount is always the order total; the client never sends one.
     * Validates the card with the Luhn check first, before touching anything. A failed payment is recorded and
     * reported as PaymentFailedException (402) and leaves the order CONFIRMED, never PAID. Retrying is allowed.
     */
    PaymentResponse process(Long userId, ProcessPaymentRequest request);

    PaymentStatusResponse status(Long userId, Long orderId);

    List<PaymentMethodResponse> methods();
}
