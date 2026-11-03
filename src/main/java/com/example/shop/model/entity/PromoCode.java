package com.example.shop.model.entity;

import com.example.shop.model.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "promo_codes", indexes = {
        @Index(name = "idx_promo_code", columnList = "code", unique = true)
})
@Getter @Setter
@NoArgsConstructor
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType discountType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    // null = unlimited
    private Integer maxUsageCount;

    @Column(nullable = false)
    private Integer currentUsageCount = 0;

    private Integer maxUsagePerUser;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    @Column(nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // empty = applies to all categories / all products
    @ManyToMany
    @JoinTable(name = "promo_code_categories",
            joinColumns = @JoinColumn(name = "promo_code_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> applicableCategories = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "promo_code_products",
            joinColumns = @JoinColumn(name = "promo_code_id"),
            inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<Product> applicableProducts = new HashSet<>();
}
