package com.example.shop.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wishlists",
        indexes = {
                @Index(name = "idx_wishlist_user", columnList = "user_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uq_wishlist_user_product", columnNames = {"user_id", "product_id"}))
@Getter @Setter
@NoArgsConstructor
public class Wishlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtAdd;

    @Column(nullable = false)
    private Boolean notifyOnPriceDrop = false;

    @Column(nullable = false)
    private Boolean notifyOnBackInStock = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    void onCreate() { addedAt = LocalDateTime.now(); }
}
