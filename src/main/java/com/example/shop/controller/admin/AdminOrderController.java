package com.example.shop.controller.admin;

import com.example.shop.dto.request.UpdateOrderNoteRequest;
import com.example.shop.dto.request.UpdateOrderStatusRequest;
import com.example.shop.dto.request.UpdateTrackingRequest;
import com.example.shop.dto.response.AdminOrderResponse;
import com.example.shop.model.enums.OrderStatus;
import com.example.shop.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Order processing: ADMIN and MANAGER. */
@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class AdminOrderController {
    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public Page<AdminOrderResponse> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return orders.adminList(status, search, from, to, pageable);
    }

    @GetMapping("/{id}")
    public AdminOrderResponse one(@PathVariable Long id) {
        return orders.adminFindById(id);
    }

    @PutMapping("/{id}/status")
    public AdminOrderResponse status(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orders.updateStatus(id, request.status());
    }

    @PutMapping("/{id}/tracking")
    public AdminOrderResponse tracking(@PathVariable Long id, @Valid @RequestBody UpdateTrackingRequest request) {
        return orders.updateTracking(id, request);
    }

    @PutMapping("/{id}/note")
    public AdminOrderResponse note(@PathVariable Long id, @Valid @RequestBody UpdateOrderNoteRequest request) {
        return orders.updateNote(id, request);
    }
}
