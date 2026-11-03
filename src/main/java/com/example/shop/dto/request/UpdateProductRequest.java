package com.example.shop.dto.request;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Partial update: a null field means "leave unchanged". sku and stock have their own endpoints. */
public record UpdateProductRequest(
        @Size(min = 2, max = 200) String name,
        String description,
        @Size(max = 500) String shortDescription,
        @Positive BigDecimal basePrice,
        @Positive BigDecimal discountPrice,
        LocalDateTime discountStartDate,
        LocalDateTime discountEndDate,
        @PositiveOrZero Integer lowStockThreshold,
        @Positive BigDecimal weight,
        @Size(max = 100) String dimensions,
        Boolean isFeatured,
        Boolean isNewArrival,
        Long categoryId,
        Long brandId) {
}
