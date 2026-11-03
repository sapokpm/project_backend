package com.example.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyPromoRequest(@NotBlank @Size(max = 50) String code) {
}
