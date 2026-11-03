package com.example.shop.repository;

import com.example.shop.model.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    /** Guest carts only: a user's cart is never reachable through a session id. */
    Optional<Cart> findBySessionIdAndUserIsNull(String sessionId);
}
