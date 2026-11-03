package com.example.shop.dto.response;

import java.math.BigDecimal;

public record ProductVariantResponse(Long id, String sku, String name, String attributes, BigDecimal price,
                                     int availableQuantity, String imageUrl, boolean isActive) {
}
