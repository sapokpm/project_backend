package com.example.shop.dto.request;

import com.example.shop.model.enums.DeliveryMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull Long addressId,
        @NotNull DeliveryMethod deliveryMethod,
        @PositiveOrZero Integer bonusPointsToUse,
        @Size(max = 1000) String customerNote) {
}
