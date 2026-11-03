package com.example.shop.service;

import com.example.shop.dto.request.WishlistRequest;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.dto.response.WishlistItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WishlistService {
    Page<WishlistItemResponse> list(Long userId, Pageable pageable);

    /** priceAtAdd is recorded now, so a later price drop can be shown. Adding twice is a 409. */
    WishlistItemResponse add(Long userId, Long productId, WishlistRequest request);

    void remove(Long userId, Long productId);

    /** Adds one unit to the cart, then removes the wishlist entry. If the cart refuses (stock), the entry stays. */
    CartResponse moveToCart(Long userId, Long productId);
}
