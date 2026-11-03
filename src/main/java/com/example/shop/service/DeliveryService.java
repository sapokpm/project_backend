package com.example.shop.service;

import com.example.shop.dto.response.DeliveryOptionResponse;
import com.example.shop.model.enums.DeliveryMethod;

import java.math.BigDecimal;
import java.util.List;

public interface DeliveryService {
    List<DeliveryOptionResponse> activeOptions();

    /** Cost from the option's base price (free when subtotal reaches its threshold) and the estimated delivery window. */
    DeliveryQuote quote(DeliveryMethod method, BigDecimal subtotal);
}
