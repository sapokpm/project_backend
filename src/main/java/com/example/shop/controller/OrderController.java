package com.example.shop.controller;

import com.example.shop.dto.request.CreateOrderRequest;
import com.example.shop.dto.response.OrderResponse;
import com.example.shop.dto.response.ReorderResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Every method works on the caller's own orders only. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal UserPrincipal me,
                                                @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orders.create(me.getId(), request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping
    public Page<OrderResponse> list(@AuthenticationPrincipal UserPrincipal me, @PageableDefault(size = 10) Pageable pageable) {
        return orders.list(me.getId(), pageable);
    }

    /** /{id} and /{orderNumber} share one URL shape: digits mean an id, anything else is an order number such as ORD-2026-0001. */
    @GetMapping("/{idOrNumber}")
    public OrderResponse one(@AuthenticationPrincipal UserPrincipal me, @PathVariable String idOrNumber) {
        return idOrNumber.chars().allMatch(Character::isDigit) && idOrNumber.length() <= 18
                ? orders.findById(me.getId(), Long.parseLong(idOrNumber))
                : orders.findByNumber(me.getId(), idOrNumber);
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return orders.cancel(me.getId(), id);
    }

    @PostMapping("/{id}/reorder")
    public ReorderResponse reorder(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long id) {
        return orders.reorder(me.getId(), id);
    }
}
