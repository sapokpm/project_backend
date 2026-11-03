package com.example.shop.service;

import com.example.shop.dto.request.CreateOrderRequest;
import com.example.shop.dto.request.UpdateOrderNoteRequest;
import com.example.shop.dto.request.UpdateTrackingRequest;
import com.example.shop.dto.response.AdminOrderResponse;
import com.example.shop.dto.response.OrderResponse;
import com.example.shop.dto.response.ReorderResponse;
import com.example.shop.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface OrderService {
    // ---- customer: every method is scoped to the caller; someone else's order is a 404 ----

    /**
     * Turns the caller's cart into an order in ONE transaction: reserves stock, applies the promo code, computes the
     * total, records promo usage and empties the cart. If anything fails, nothing is kept.
     */
    OrderResponse create(Long userId, CreateOrderRequest request);

    Page<OrderResponse> list(Long userId, Pageable pageable);

    OrderResponse findById(Long userId, Long orderId);

    OrderResponse findByNumber(Long userId, String orderNumber);

    /** Customers may cancel while the order is PENDING or CONFIRMED. Releases stock and the promo use. */
    OrderResponse cancel(Long userId, Long orderId);

    /** Puts the order's items back in the cart at today's prices; lines that cannot be added are reported. */
    ReorderResponse reorder(Long userId, Long orderId);

    // ---- staff (admin controller later) ----

    Page<AdminOrderResponse> adminList(OrderStatus status, String search, LocalDate from, LocalDate to, Pageable pageable);

    AdminOrderResponse adminFindById(Long orderId);

    /** Follows the status machine. DELIVERED -> REFUNDED is refused here: it only happens through a return. */
    AdminOrderResponse updateStatus(Long orderId, OrderStatus status);

    AdminOrderResponse updateTracking(Long orderId, UpdateTrackingRequest request);

    AdminOrderResponse updateNote(Long orderId, UpdateOrderNoteRequest request);
}
