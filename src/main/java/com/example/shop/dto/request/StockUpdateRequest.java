package com.example.shop.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpdateRequest(@NotNull @PositiveOrZero Integer stockQuantity) {
}
