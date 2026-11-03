package com.example.shop.dto.request;

import com.example.shop.model.enums.DeliveryMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record DeliveryCalculateRequest(
        @NotNull DeliveryMethod deliveryMethod,
        @NotNull @PositiveOrZero BigDecimal subtotal) {
}
