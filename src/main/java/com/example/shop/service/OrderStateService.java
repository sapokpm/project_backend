package com.example.shop.service;

import com.example.shop.exception.InvalidStatusTransitionException;
import com.example.shop.exception.OrderCancellationException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.repository.OrderRepository;
import com.example.shop.model.entity.Order;
import com.example.shop.model.entity.OrderItem;
import com.example.shop.model.entity.Payment;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.model.enums.PaymentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The single place an order's status changes. Checks the transition, then does what the new status means
 * for stock, promo usage, payment and timestamps. Orders, payments and staff all go through here.
 */
@Component
public class OrderStateService {
    private final OrderRepository orders;
    private final ProductService products;
    private final PromoCodeService promos;

    public OrderStateService(OrderRepository orders, ProductService products, PromoCodeService promos) {
        this.orders = orders;
        this.products = products;
        this.promos = promos;
    }

    /**
     * Moves the order to the new status or throws, and returns the updated order. Takes the id, not the entity:
     * the stock and promo changes below are bulk updates that detach everything loaded before them, so the
     * order is read again afterwards and the status change is made on that fresh copy.
     */
    @Transactional
    public Order apply(Long orderId, OrderStatus to) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("No order with id " + orderId));
        OrderStatus from = order.getStatus();
        if (!OrderStatusMachine.canTransition(from, to)) {
            throw to == OrderStatus.CANCELLED
                    ? new OrderCancellationException("An order that is " + from + " cannot be cancelled")
                    : new InvalidStatusTransitionException("An order cannot move from " + from + " to " + to);
        }

        // 1. side effects on stock and promo usage (bulk updates)
        List<OrderItem> items = order.getOrderItems();
        boolean release = to == OrderStatus.CANCELLED || (to == OrderStatus.REFUNDED && from == OrderStatus.PAID);
        for (OrderItem i : items) {
            if (to == OrderStatus.SHIPPED) {
                products.commitStock(i.getProduct().getId(), variantId(i), i.getQuantity()); // reserved -> gone
            } else if (release) {
                products.releaseStock(i.getProduct().getId(), variantId(i), i.getQuantity()); // nothing shipped yet
            }
        }
        if (release) promos.releaseUsage(orderId);

        // 2. the status change itself, on a fresh copy
        order = orders.findById(orderId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        switch (to) {
            case PAID -> {
                if (order.getPaidAt() == null) order.setPaidAt(now);
                Payment pay = order.getPayment();
                if (pay != null && pay.getStatus() == PaymentStatus.PENDING) { // cash on delivery, collected
                    pay.setStatus(PaymentStatus.COMPLETED);
                    pay.setPaidAt(now);
                }
            }
            case SHIPPED -> order.setShippedAt(now);
            case DELIVERED -> {
                order.setDeliveredAt(now);
                order.setActualDeliveryDate(LocalDate.now());
            }
            case CANCELLED, REFUNDED -> refundIfPaid(order);
            default -> {
            }
        }
        order.setStatus(to);
        return order;
    }

    private static void refundIfPaid(Order order) {
        Payment p = order.getPayment();
        if (p != null && p.getStatus() == PaymentStatus.COMPLETED) {
            p.setStatus(PaymentStatus.REFUNDED);
            p.setRefundedAmount(p.getAmount());
        }
    }

    private static Long variantId(OrderItem i) {
        return i.getVariant() == null ? null : i.getVariant().getId();
    }
}
