package com.example.shop.controller;

import com.example.shop.dto.request.ProcessPaymentRequest;
import com.example.shop.dto.response.PaymentMethodResponse;
import com.example.shop.dto.response.PaymentResponse;
import com.example.shop.dto.response.PaymentStatusResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/process")
    public PaymentResponse process(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody ProcessPaymentRequest request) {
        return payments.process(me.getId(), request);
    }

    @GetMapping("/{orderId}/status")
    public PaymentStatusResponse status(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long orderId) {
        return payments.status(me.getId(), orderId);
    }

    @GetMapping("/methods")
    public List<PaymentMethodResponse> methods() {
        return payments.methods();
    }
}
