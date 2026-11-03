package com.example.shop.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * promoWarning is set when the applied code no longer works (expired, minimum no longer met...):
 * discount is then 0 and the customer can remove the code.
 */
public record CartResponse(
        Long id,
        List<CartItemResponse> items,
        int totalItems,
        BigDecimal subtotal,
        String appliedPromoCode,
        BigDecimal discount,
        boolean freeShipping,
        String promoWarning,
        BigDecimal total) {
}
