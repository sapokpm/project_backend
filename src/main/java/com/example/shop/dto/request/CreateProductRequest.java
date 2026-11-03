package com.example.shop.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProductRequest(
        @NotBlank @Size(min = 2, max = 200) String name,
        String description,
        @Size(max = 500) String shortDescription,
        @NotBlank @Size(max = 64) String sku,
        @NotNull @Positive BigDecimal basePrice,
        @Positive BigDecimal discountPrice,
        LocalDateTime discountStartDate,
        LocalDateTime discountEndDate,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero Integer lowStockThreshold,
        @Positive BigDecimal weight,
        @Size(max = 100) String dimensions,
        Boolean isFeatured,
        Boolean isNewArrival,
        @NotNull Long categoryId,
        Long brandId) {
}
