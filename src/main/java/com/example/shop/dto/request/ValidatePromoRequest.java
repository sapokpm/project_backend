package com.example.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ValidatePromoRequest(
        @NotBlank @Size(max = 50) String code,
        @PositiveOrZero BigDecimal orderAmount) {
}
