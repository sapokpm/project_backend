package com.example.shop.dto.response;

import com.example.shop.model.enums.PaymentMethod;
import com.example.shop.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// only the last four digits are ever exposed
public record PaymentResponse(
        Long id,
        Long orderId,
        String transactionId,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String cardLastFourDigits,
        LocalDateTime paidAt,
        String errorMessage,
        BigDecimal refundedAmount) {
}
