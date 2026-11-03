package com.example.shop.controller;

import com.example.shop.dto.request.DeliveryCalculateRequest;
import com.example.shop.dto.response.DeliveryCostResponse;
import com.example.shop.dto.response.DeliveryOptionResponse;
import com.example.shop.service.DeliveryQuote;
import com.example.shop.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {
    private final DeliveryService delivery;

    public DeliveryController(DeliveryService delivery) {
        this.delivery = delivery;
    }

    // four fixed options, so a plain list rather than a page
    @GetMapping("/methods")
    public List<DeliveryOptionResponse> methods() {
        return delivery.activeOptions();
    }

    /** Cost and delivery window for a given subtotal, so a cart page can show them before checkout. */
    @PostMapping("/calculate")
    public DeliveryCostResponse calculate(@Valid @RequestBody DeliveryCalculateRequest request) {
        DeliveryQuote q = delivery.quote(request.deliveryMethod(), request.subtotal());
        return new DeliveryCostResponse(q.code(), q.cost(), q.free(), q.estimatedFrom(), q.estimatedTo());
    }
}
