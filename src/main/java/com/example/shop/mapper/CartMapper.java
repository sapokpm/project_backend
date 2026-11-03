package com.example.shop.mapper;

import com.example.shop.dto.response.CartItemResponse;
import com.example.shop.dto.response.CartResponse;
import com.example.shop.dto.response.WishlistItemResponse;
import com.example.shop.model.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CartMapper {

    /** discount comes from the service (promo rules); the mapper only presents it. */
    public CartResponse toResponse(Cart cart, BigDecimal discount, boolean freeShipping, String promoWarning) {
        List<CartItemResponse> items = cart.getCartItems().stream().map(this::toResponse).toList();
        BigDecimal subtotal = items.stream().map(CartItemResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal d = discount == null ? BigDecimal.ZERO : discount;
        BigDecimal total = subtotal.subtract(d).max(BigDecimal.ZERO);
        int count = items.stream().mapToInt(CartItemResponse::quantity).sum();
        return new CartResponse(cart.getId(), items, count, subtotal, cart.getAppliedPromoCode(), d, freeShipping,
                promoWarning, total);
    }

    public CartItemResponse toResponse(CartItem i) {
        Product p = i.getProduct();
        ProductVariant v = i.getVariant();
        BigDecimal unit = v != null ? v.getPrice() : ProductMapper.currentPrice(p);
        // a deactivated product or variant can no longer be bought, whatever its stock says
        boolean sellable = Boolean.TRUE.equals(p.getIsActive()) && (v == null || Boolean.TRUE.equals(v.getIsActive()));
        int available = !sellable ? 0 : v != null ? Math.max(0, v.getStockQuantity() - v.getReservedQuantity())
                : ProductMapper.available(p);
        String image = v != null && v.getImageUrl() != null ? v.getImageUrl()
                : p.getImages().stream().map(ProductImage::getImageUrl).findFirst().orElse(null);
        return new CartItemResponse(i.getId(), p.getId(), p.getName(), v == null ? null : v.getId(),
                v == null ? null : v.getName(), image, i.getQuantity(), i.getPriceAtAdd(), unit,
                unit.compareTo(i.getPriceAtAdd()) != 0, unit.multiply(BigDecimal.valueOf(i.getQuantity())), available);
    }

    public WishlistItemResponse toResponse(Wishlist w) {
        Product p = w.getProduct();
        BigDecimal current = ProductMapper.currentPrice(p);
        return new WishlistItemResponse(p.getId(), p.getName(),
                p.getImages().stream().map(ProductImage::getImageUrl).findFirst().orElse(null),
                w.getPriceAtAdd(), current, current.compareTo(w.getPriceAtAdd()) < 0,
                Boolean.TRUE.equals(p.getIsActive()) && ProductMapper.available(p) > 0, w.getAddedAt());
    }
}
