package com.example.shop.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The order total, in exactly this order (it is on the marking sheet):
 * <pre>
 * subtotal      = sum of (price * quantity)
 * discount      = promo discount, already capped at the code's maxDiscountAmount
 * shippingCost  = 0 if subtotal >= the delivery option's free threshold, else its base price
 * bonusUsed     = requested points, capped at 50% of (subtotal - discount)
 * totalAmount   = subtotal - discount + shippingCost - (bonusUsed * 0.01)
 * </pre>
 */
public final class OrderCalculator {
    public static final BigDecimal POINT_VALUE = new BigDecimal("0.01");
    public static final BigDecimal MAX_PAYMENT_PERCENT_BY_POINTS = new BigDecimal("0.50");

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shippingCost, int bonusPointsUsed,
                         BigDecimal totalAmount) {
    }

    private OrderCalculator() {
    }

    /** Most points that may pay for this order: 50% of (subtotal - discount), at 0.01 per point. */
    public static int maxBonusPoints(BigDecimal subtotal, BigDecimal discount) {
        BigDecimal payable = subtotal.subtract(discount).max(BigDecimal.ZERO);
        return payable.multiply(MAX_PAYMENT_PERCENT_BY_POINTS).divide(POINT_VALUE, 0, RoundingMode.DOWN).intValueExact();
    }

    public static Totals calculate(BigDecimal subtotal, BigDecimal promoDiscount, BigDecimal shippingCost,
                                   int requestedPoints, int availablePoints) {
        BigDecimal sub = subtotal.setScale(2, RoundingMode.HALF_UP);
        // a discount can never exceed what is being bought
        BigDecimal discount = promoDiscount.min(sub).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = shippingCost.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int points = Math.max(0, Math.min(Math.min(requestedPoints, availablePoints), maxBonusPoints(sub, discount)));
        BigDecimal pointsValue = POINT_VALUE.multiply(BigDecimal.valueOf(points));
        BigDecimal total = sub.subtract(discount).add(shipping).subtract(pointsValue).setScale(2, RoundingMode.HALF_UP);
        return new Totals(sub, discount, shipping, points, total);
    }
}
