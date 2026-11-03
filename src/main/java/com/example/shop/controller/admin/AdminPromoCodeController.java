package com.example.shop.controller.admin;

import com.example.shop.dto.request.PromoCodeRequest;
import com.example.shop.dto.response.PromoCodeResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.PromoCodeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/promo-codes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPromoCodeController {
    private final PromoCodeService promos;

    public AdminPromoCodeController(PromoCodeService promos) {
        this.promos = promos;
    }

    @GetMapping
    public Page<PromoCodeResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return promos.list(pageable);
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> create(@AuthenticationPrincipal UserPrincipal admin,
                                                    @Valid @RequestBody PromoCodeRequest request) {
        PromoCodeResponse created = promos.create(admin.getId(), request);
        return ResponseEntity.created(URI.create("/api/admin/promo-codes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public PromoCodeResponse update(@PathVariable Long id, @Valid @RequestBody PromoCodeRequest request) {
        return promos.update(id, request);
    }

    /** Unused codes are deleted; codes that have been used are deactivated so order history stays intact. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        promos.delete(id);
        return ResponseEntity.noContent().build();
    }
}
