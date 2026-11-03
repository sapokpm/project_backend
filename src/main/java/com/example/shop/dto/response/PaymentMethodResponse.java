package com.example.shop.dto.response;

import com.example.shop.model.enums.PaymentMethod;

public record PaymentMethodResponse(PaymentMethod method, String label) {
}
