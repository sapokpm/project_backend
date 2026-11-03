package com.example.shop.service;

import com.example.shop.model.entity.PromoCode;

import java.math.BigDecimal;

/** discount is the money off the items; freeShipping is set instead for FREE_SHIPPING codes. */
public record PromoResult(PromoCode promo, BigDecimal discount, boolean freeShipping) {
}
