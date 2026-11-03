package com.example.shop.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "brands", indexes = {
        @Index(name = "idx_brand_slug", columnList = "slug", unique = true)
})
@Getter @Setter
@NoArgsConstructor
public class Brand {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String websiteUrl;

    @Column(length = 100)
    private String country;

    @Column(nullable = false)
    private Boolean isActive = true;
}
