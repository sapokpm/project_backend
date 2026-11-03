package com.example.shop.dto.response;

import java.util.List;

/** Staff view: the order plus the customer and the internal note. */
public record AdminOrderResponse(OrderResponse order, Long userId, String customerEmail, String adminNote) {
}
