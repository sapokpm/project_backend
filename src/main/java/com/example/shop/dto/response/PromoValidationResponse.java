package com.example.shop.dto.response;

import com.example.shop.model.enums.DiscountType;

import java.math.BigDecimal;

public record PromoValidationResponse(String code, DiscountType discountType, BigDecimal discountValue,
                                      BigDecimal discountAmount, String message) {
}
