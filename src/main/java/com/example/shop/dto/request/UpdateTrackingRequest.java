package com.example.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTrackingRequest(
        @NotBlank @Size(max = 100) String trackingNumber,
        LocalDate estimatedDeliveryDate) {
}
