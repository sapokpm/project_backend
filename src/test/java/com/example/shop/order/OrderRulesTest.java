package com.example.shop.order;

import com.example.shop.model.enums.OrderStatus;
import com.example.shop.service.OrderCalculator;
import com.example.shop.service.OrderStatusMachine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.shop.model.enums.OrderStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The two pure rules from Part 11: the total arithmetic and the status machine. No Spring, no database. */
class OrderRulesTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    // ------------------------------------------------------------ total

    @Test
    void totalIsSubtotalMinusDiscountPlusShipping() {
        var t = OrderCalculator.calculate(d("100"), d("10"), d("5"), 0, 0);
        assertThat(t.subtotal()).isEqualByComparingTo("100.00");
        assertThat(t.discount()).isEqualByComparingTo("10.00");
        assertThat(t.shippingCost()).isEqualByComparingTo("5.00");
        assertThat(t.bonusPointsUsed()).isZero();
        assertThat(t.totalAmount()).isEqualByComparingTo("95.00");
    }

    @Test
    void pointsAreCappedAtHalfOfSubtotalMinusDiscount() {
        // (100 - 20) * 50% = 40.00 = 4000 points, even though 10000 were asked for and available
        var t = OrderCalculator.calculate(d("100"), d("20"), BigDecimal.ZERO, 10_000, 10_000);
        assertThat(t.bonusPointsUsed()).isEqualTo(4000);
        assertThat(t.totalAmount()).isEqualByComparingTo("40.00"); // 100 - 20 + 0 - 40
    }

    @Test
    void shippingIsNotPartOfTheBonusCap() {
        var t = OrderCalculator.calculate(d("100"), BigDecimal.ZERO, d("20"), 10_000, 10_000);
        assertThat(t.bonusPointsUsed()).isEqualTo(5000); // 50% of 100, not of 120
        assertThat(t.totalAmount()).isEqualByComparingTo("70.00"); // 100 + 20 - 50
    }

    @Test
    void pointsCannotExceedWhatTheCustomerHas() {
        var t = OrderCalculator.calculate(d("100"), BigDecimal.ZERO, BigDecimal.ZERO, 500, 300);
        assertThat(t.bonusPointsUsed()).isEqualTo(300);
        assertThat(t.totalAmount()).isEqualByComparingTo("97.00");
    }

    @Test
    void negativeRequestsAndOversizedDiscountsAreClamped() {
        assertThat(OrderCalculator.calculate(d("50"), BigDecimal.ZERO, BigDecimal.ZERO, -10, 100).bonusPointsUsed()).isZero();
        var t = OrderCalculator.calculate(d("30"), d("500"), d("5"), 0, 0); // discount larger than the goods
        assertThat(t.discount()).isEqualByComparingTo("30.00");
        assertThat(t.totalAmount()).isEqualByComparingTo("5.00"); // only shipping left, never negative
    }

    @Test
    void amountsAreRoundedToCents() {
        var t = OrderCalculator.calculate(d("33.335"), d("3.3335"), d("0"), 0, 0);
        assertThat(t.subtotal()).isEqualByComparingTo("33.34");
        assertThat(t.discount()).isEqualByComparingTo("3.33");
        assertThat(t.totalAmount()).isEqualByComparingTo("30.01");
        assertThat(OrderCalculator.maxBonusPoints(d("0.99"), BigDecimal.ZERO)).isEqualTo(49); // rounds down, never up
    }

    // ----------------------------------------------------------- statuses

    private static final Map<OrderStatus, Set<OrderStatus>> DOC = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(PAID, CANCELLED),
            PAID, EnumSet.of(PROCESSING, REFUNDED),
            PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(IN_TRANSIT),
            IN_TRANSIT, EnumSet.of(DELIVERED),
            DELIVERED, EnumSet.of(REFUNDED),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            REFUNDED, EnumSet.noneOf(OrderStatus.class));

    @Test
    void everyPairMatchesTheTableInTheBrief() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertThat(OrderStatusMachine.canTransition(from, to))
                        .as("%s -> %s", from, to).isEqualTo(DOC.get(from).contains(to));
            }
        }
    }

    @Test
    void anOrderCanWalkThePathFromPendingToDelivered() {
        OrderStatus[] path = {PENDING, CONFIRMED, PAID, PROCESSING, SHIPPED, IN_TRANSIT, DELIVERED};
        for (int i = 0; i < path.length - 1; i++) {
            assertThat(OrderStatusMachine.canTransition(path[i], path[i + 1])).as("%s -> %s", path[i], path[i + 1]).isTrue();
        }
    }

    @Test
    void thingsThatMustNeverHappen() {
        assertThat(OrderStatusMachine.canTransition(DELIVERED, PENDING)).isFalse();
        assertThat(OrderStatusMachine.canTransition(SHIPPED, CANCELLED)).isFalse(); // already left the warehouse
        assertThat(OrderStatusMachine.canTransition(PAID, CANCELLED)).isFalse();    // refund instead
        assertThat(OrderStatusMachine.canTransition(PENDING, SHIPPED)).isFalse();   // no skipping steps
        assertThat(OrderStatusMachine.canTransition(PENDING, PENDING)).isFalse();
        assertThat(OrderStatusMachine.canTransition(null, PAID)).isFalse();
        assertThat(OrderStatusMachine.allowedFrom(CANCELLED)).isEmpty();
        assertThat(OrderStatusMachine.allowedFrom(REFUNDED)).isEmpty();
    }
}
