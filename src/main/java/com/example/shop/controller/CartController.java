package com.example.shop.controller;

import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.dto.request.ApplyPromoRequest;
import com.example.shop.dto.request.MergeCartRequest;
import com.example.shop.dto.request.UpdateCartItemRequest;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.CartOwner;
import com.example.shop.service.CartService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Works for logged-in users (their own cart) and guests. A guest is identified by the X-Cart-Session header:
 * the server issues it on the first write and every guest response repeats it; the client sends it back.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    static final String SESSION_HEADER = "X-Cart-Session";

    private final CartService carts;

    public CartController(CartService carts) {
        this.carts = carts;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal UserPrincipal me,
                            @RequestHeader(value = SESSION_HEADER, required = false) String session,
                            HttpServletResponse res) {
        return carts.get(owner(me, session, false, res));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@AuthenticationPrincipal UserPrincipal me,
                                                @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                                @Valid @RequestBody AddCartItemRequest request,
                                                HttpServletResponse res) {
        return ResponseEntity.status(HttpStatus.CREATED).body(carts.addItem(owner(me, session, true, res), request));
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@AuthenticationPrincipal UserPrincipal me,
                                   @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                   @PathVariable Long itemId,
                                   @Valid @RequestBody UpdateCartItemRequest request,
                                   HttpServletResponse res) {
        return carts.updateItem(owner(me, session, false, res), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@AuthenticationPrincipal UserPrincipal me,
                                   @RequestHeader(value = SESSION_HEADER, required = false) String session,
                                   @PathVariable Long itemId,
                                   HttpServletResponse res) {
        return carts.removeItem(owner(me, session, false, res), itemId);
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal UserPrincipal me,
                              @RequestHeader(value = SESSION_HEADER, required = false) String session,
                              HttpServletResponse res) {
        return carts.clear(owner(me, session, false, res));
    }

    /** Login required: folds the guest cart into the caller's own. */
    @PostMapping("/merge")
    public CartResponse merge(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody MergeCartRequest request) {
        return carts.merge(me.getId(), parseSession(request.sessionId()));
    }

    /** Login required: per-customer usage limits need to know who is asking. */
    @PostMapping("/apply-promo")
    public CartResponse applyPromo(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody ApplyPromoRequest request) {
        return carts.applyPromo(me.getId(), request.code());
    }

    @DeleteMapping("/promo")
    public CartResponse removePromo(@AuthenticationPrincipal UserPrincipal me) {
        return carts.removePromo(me.getId());
    }

    private CartOwner owner(UserPrincipal me, String header, boolean createIfMissing, HttpServletResponse res) {
        if (me != null) return CartOwner.user(me.getId());
        String session = header == null || header.isBlank() ? null : parseSession(header);
        if (session == null && createIfMissing) session = UUID.randomUUID().toString();
        if (session != null) res.setHeader(SESSION_HEADER, session);
        return CartOwner.guest(session);
    }

    /** Session ids are server-issued random UUIDs, so anything else is rejected rather than stored. */
    private static String parseSession(String raw) {
        try {
            return UUID.fromString(raw.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid " + SESSION_HEADER);
        }
    }
}
