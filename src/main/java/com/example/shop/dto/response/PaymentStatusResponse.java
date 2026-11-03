package com.example.shop.dto.response;

import com.example.shop.model.enums.OrderStatus;
import com.example.shop.model.enums.PaymentStatus;

/** paymentStatus is null while no payment has been attempted. */
public record PaymentStatusResponse(Long orderId, OrderStatus orderStatus, PaymentStatus paymentStatus,
                                    PaymentResponse payment) {
}
