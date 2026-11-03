package com.example.shop.service;

import com.example.shop.mapper.ProductMapper;
import com.example.shop.model.entity.Cart;
import com.example.shop.model.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Current-price view of a cart, shared by the cart and promo services. */
@Component
public class CartPricing {
    public List<PromoLine> lines(Cart cart) {
        return cart.getCartItems().stream().map(this::line).toList();
    }

    public BigDecimal subtotal(List<PromoLine> lines) {
        return lines.stream().map(PromoLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What one unit costs right now (the variant's price, else the product's current price). */
    public BigDecimal unitPrice(CartItem i) {
        return i.getVariant() != null ? i.getVariant().getPrice() : ProductMapper.currentPrice(i.getProduct());
    }

    private PromoLine line(CartItem i) {
        BigDecimal unit = unitPrice(i);
        return new PromoLine(i.getProduct().getId(), i.getProduct().getCategory().getId(),
                unit.multiply(BigDecimal.valueOf(i.getQuantity())));
    }
}
