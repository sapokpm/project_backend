package com.example.shop.dto.response;

import java.math.BigDecimal;

/** Lighter product for list pages. */
public record ProductSummaryResponse(
        Long id,
        String name,
        String slug,
        BigDecimal basePrice,
        BigDecimal currentPrice,
        boolean onSale,
        boolean inStock,
        BigDecimal averageRating,
        int reviewCount,
        String primaryImageUrl,
        String brandName) {
}
