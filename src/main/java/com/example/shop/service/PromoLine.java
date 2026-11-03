package com.example.shop.service;

import java.math.BigDecimal;

/** One cart or order line as the promo rules see it. */
public record PromoLine(Long productId, Long categoryId, BigDecimal lineTotal) {
}
