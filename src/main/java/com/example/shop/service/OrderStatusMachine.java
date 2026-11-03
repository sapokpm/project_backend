package com.example.shop.service;

import com.example.shop.model.enums.OrderStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.example.shop.model.enums.OrderStatus.*;

/**
 * The allowed order status transitions, and nothing else. Pure logic with no dependencies, so it is trivial to test.
 *
 * <pre>
 * PENDING    -> CONFIRMED, CANCELLED
 * CONFIRMED  -> PAID, CANCELLED
 * PAID       -> PROCESSING, REFUNDED
 * PROCESSING -> SHIPPED, CANCELLED
 * SHIPPED    -> IN_TRANSIT
 * IN_TRANSIT -> DELIVERED
 * DELIVERED  -> REFUNDED   (only through a return)
 * CANCELLED, REFUNDED -> nothing
 * </pre>
 */
public final class OrderStatusMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = new EnumMap<>(OrderStatus.class);

    static {
        NEXT.put(PENDING, EnumSet.of(CONFIRMED, CANCELLED));
        NEXT.put(CONFIRMED, EnumSet.of(PAID, CANCELLED));
        NEXT.put(PAID, EnumSet.of(PROCESSING, REFUNDED));
        NEXT.put(PROCESSING, EnumSet.of(SHIPPED, CANCELLED));
        NEXT.put(SHIPPED, EnumSet.of(IN_TRANSIT));
        NEXT.put(IN_TRANSIT, EnumSet.of(DELIVERED));
        NEXT.put(DELIVERED, EnumSet.of(REFUNDED));
        NEXT.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
        NEXT.put(REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStatusMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return from != null && to != null && NEXT.get(from).contains(to);
    }

    public static Set<OrderStatus> allowedFrom(OrderStatus from) {
        return java.util.Collections.unmodifiableSet(NEXT.get(from));
    }
}
