package com.example.shop.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryQuote(String code, String name, BigDecimal cost, boolean free, LocalDate estimatedFrom,
                            LocalDate estimatedTo) {
}
