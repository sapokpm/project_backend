package com.example.shop.controller;

import com.example.shop.dto.request.WishlistRequest;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.dto.response.WishlistItemResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.WishlistService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {
    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    public Page<WishlistItemResponse> list(@AuthenticationPrincipal UserPrincipal me,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return wishlist.list(me.getId(), pageable);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<WishlistItemResponse> add(@AuthenticationPrincipal UserPrincipal me,
                                                    @PathVariable Long productId,
                                                    @RequestBody(required = false) WishlistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(wishlist.add(me.getId(), productId, request));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long productId) {
        wishlist.remove(me.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/move-to-cart")
    public CartResponse moveToCart(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long productId) {
        return wishlist.moveToCart(me.getId(), productId);
    }
}
