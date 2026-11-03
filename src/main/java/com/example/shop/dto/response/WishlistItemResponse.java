package com.example.shop.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WishlistItemResponse(
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal priceAtAdd,
        BigDecimal currentPrice,
        boolean priceDropped,
        boolean inStock,
        LocalDateTime addedAt) {
}
