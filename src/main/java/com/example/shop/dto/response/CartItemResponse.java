package com.example.shop.dto.response;

import java.math.BigDecimal;

/** unitPrice is always the current price; priceChanged flags a difference from priceAtAdd. */
public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        Long variantId,
        String variantName,
        String imageUrl,
        int quantity,
        BigDecimal priceAtAdd,
        BigDecimal unitPrice,
        boolean priceChanged,
        BigDecimal lineTotal,
        int availableQuantity) {
}
