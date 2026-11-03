package com.example.shop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull Long productId,
        Long variantId,
        @NotNull @Min(1) @Max(100) Integer quantity) {
}
