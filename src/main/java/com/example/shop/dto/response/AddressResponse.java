package com.example.shop.dto.response;

import java.math.BigDecimal;

public record AddressResponse(
        Long id,
        String title,
        String country,
        String city,
        String street,
        String building,
        String apartment,
        String postalCode,
        String recipientName,
        String recipientPhone,
        boolean isDefault,
        BigDecimal latitude,
        BigDecimal longitude) {
}
