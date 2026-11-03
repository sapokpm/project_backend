package com.example.shop.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "delivery_options", indexes = {
        @Index(name = "idx_delivery_code", columnList = "code", unique = true)
})
@Getter @Setter
@NoArgsConstructor
public class DeliveryOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    // order subtotal at or above this ships free; null = never free
    @Column(precision = 12, scale = 2)
    private BigDecimal freeThreshold;

    @Column(nullable = false)
    private Integer estimatedDaysMin;

    @Column(nullable = false)
    private Integer estimatedDaysMax;

    @Column(nullable = false)
    private Boolean isActive = true;
}
