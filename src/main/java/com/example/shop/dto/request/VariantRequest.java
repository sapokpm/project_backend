package com.example.shop.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record VariantRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        String attributes,
        @NotNull @Positive BigDecimal price,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @Size(max = 500) String imageUrl) {
}
