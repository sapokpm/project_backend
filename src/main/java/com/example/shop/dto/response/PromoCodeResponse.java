package com.example.shop.dto.response;

import com.example.shop.model.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record PromoCodeResponse(
        Long id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer maxUsageCount,
        int currentUsageCount,
        Integer maxUsagePerUser,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        boolean isActive,
        Set<Long> applicableCategoryIds,
        Set<Long> applicableProductIds) {
}
