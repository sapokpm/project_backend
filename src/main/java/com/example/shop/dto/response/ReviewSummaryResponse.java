package com.example.shop.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/** distribution maps star (1-5) to review count. */
public record ReviewSummaryResponse(BigDecimal averageRating, long totalReviews, Map<Integer, Long> distribution) {
}
