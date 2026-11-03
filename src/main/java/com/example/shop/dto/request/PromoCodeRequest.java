package com.example.shop.dto.request;

import com.example.shop.model.enums.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record PromoCodeRequest(
        @NotBlank @Size(max = 50) String code,
        @Size(max = 500) String description,
        @NotNull DiscountType discountType,
        @NotNull @PositiveOrZero BigDecimal discountValue,
        @PositiveOrZero BigDecimal minOrderAmount,
        @PositiveOrZero BigDecimal maxDiscountAmount,
        @Positive Integer maxUsageCount,
        @Positive Integer maxUsagePerUser,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        Boolean isActive,
        Set<Long> applicableCategoryIds,
        Set<Long> applicableProductIds) {
}
