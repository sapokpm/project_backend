package com.example.shop.dto.request;

/** Optional body when adding to the wishlist; both flags default to false. */
public record WishlistRequest(Boolean notifyOnPriceDrop, Boolean notifyOnBackInStock) {
}
