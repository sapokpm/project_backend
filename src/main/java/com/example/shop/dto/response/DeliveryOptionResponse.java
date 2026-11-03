package com.example.shop.dto.response;

import java.math.BigDecimal;

public record DeliveryOptionResponse(Long id, String name, String code, String description, BigDecimal basePrice,
                                     BigDecimal freeThreshold, int estimatedDaysMin, int estimatedDaysMax) {
}
