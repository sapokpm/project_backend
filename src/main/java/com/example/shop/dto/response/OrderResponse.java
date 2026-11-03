package com.example.shop.dto.response;

import com.example.shop.model.enums.DeliveryMethod;
import com.example.shop.model.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** adminNote is deliberately absent; it is internal. */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        ShippingAddressResponse shippingAddress,
        DeliveryMethod deliveryMethod,
        String deliveryTrackingNumber,
        LocalDate estimatedDeliveryDate,
        LocalDate actualDeliveryDate,
        BigDecimal subtotal,
        BigDecimal shippingCost,
        BigDecimal discount,
        int bonusPointsUsed,
        BigDecimal totalAmount,
        String promoCode,
        String customerNote,
        List<OrderItemResponse> items,
        PaymentResponse payment,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {
}
