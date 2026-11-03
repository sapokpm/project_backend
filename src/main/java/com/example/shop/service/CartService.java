package com.example.shop.service;

import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.dto.request.UpdateCartItemRequest;
import com.example.shop.dto.response.CartResponse;

import java.util.List;

public interface CartService {
    record ReorderLine(Long productId, Long variantId, int quantity) {
    }

    /** Always current prices; items whose price moved since they were added are flagged. An unknown guest gets an empty cart. */
    CartResponse get(CartOwner owner);

    /** Checks available stock for the new total on that line. Adding a product already in the cart adds to its quantity. */
    CartResponse addItem(CartOwner owner, AddCartItemRequest request);

    CartResponse updateItem(CartOwner owner, Long itemId, UpdateCartItemRequest request);

    CartResponse removeItem(CartOwner owner, Long itemId);

    CartResponse clear(CartOwner owner);

    /**
     * Moves a guest cart into the user's cart: same product/variant lines add up (clamped to available stock),
     * the guest cart is then deleted.
     */
    CartResponse merge(Long userId, String guestSessionId);

    /**
     * Re-adds lines (for "order again"). Lines that cannot be added right now (gone, inactive, out of stock) are
     * skipped and reported, never an error. Returns the reasons.
     */
    List<String> addAvailable(Long userId, List<ReorderLine> lines);

    /** Strict check of the code against the cart as it is now; stores it on the cart. Login required. */
    CartResponse applyPromo(Long userId, String code);

    CartResponse removePromo(Long userId);

    /** Adds quantity of a product to the user's cart; used by "move wishlist item to cart". */
    CartResponse addProduct(Long userId, Long productId, int quantity);
}
