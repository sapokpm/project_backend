package com.example.shop.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Full product. currentPrice is the discount price while the discount is running, else basePrice. */
public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        String shortDescription,
        String sku,
        BigDecimal basePrice,
        BigDecimal currentPrice,
        boolean onSale,
        int availableQuantity,
        boolean inStock,
        BigDecimal weight,
        String dimensions,
        boolean isActive,
        boolean isFeatured,
        boolean isNewArrival,
        BigDecimal averageRating,
        int reviewCount,
        Long categoryId,
        String categoryName,
        Long brandId,
        String brandName,
        List<ProductImageResponse> images,
        List<ProductSpecificationResponse> specifications,
        LocalDateTime createdAt) {
}
