package com.example.shop.repository;

import com.example.shop.model.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCartId(Long cartId);

    /** Scoped to the cart, so an item id from someone else's cart is simply "not found". */
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);
}
