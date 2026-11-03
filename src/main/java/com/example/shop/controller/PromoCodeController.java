package com.example.shop.controller;

import com.example.shop.dto.request.ValidatePromoRequest;
import com.example.shop.dto.response.PromoValidationResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/promo")
public class PromoCodeController {
    private final PromoCodeService promos;

    public PromoCodeController(PromoCodeService promos) {
        this.promos = promos;
    }

    @PostMapping("/validate")
    public PromoValidationResponse validate(@AuthenticationPrincipal UserPrincipal me,
                                            @Valid @RequestBody ValidatePromoRequest request) {
        return promos.validate(me.getId(), request);
    }
}
