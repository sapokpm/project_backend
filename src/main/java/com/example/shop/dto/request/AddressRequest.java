package com.example.shop.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddressRequest(
        @Size(max = 100) String title,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 200) String street,
        @Size(max = 50) String building,
        @Size(max = 50) String apartment,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 32) String recipientPhone,
        Boolean isDefault,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {
}
