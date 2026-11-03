package com.example.shop.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryCostResponse(String code, BigDecimal cost, boolean free,
                                   LocalDate estimatedFrom, LocalDate estimatedTo) {
}
