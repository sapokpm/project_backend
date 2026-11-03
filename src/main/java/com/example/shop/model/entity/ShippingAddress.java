package com.example.shop.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Copy of the address at order time, so later edits to Address never rewrite old orders.
@Embeddable
@Getter @Setter
@NoArgsConstructor
public class ShippingAddress {
    @Column(name = "shipping_country", length = 100)
    private String country;

    @Column(name = "shipping_city", length = 100)
    private String city;

    @Column(name = "shipping_street", length = 200)
    private String street;

    @Column(name = "shipping_building", length = 50)
    private String building;

    @Column(name = "shipping_apartment", length = 50)
    private String apartment;

    @Column(name = "shipping_postal_code", length = 20)
    private String postalCode;

    @Column(name = "shipping_recipient_name", length = 200)
    private String recipientName;

    @Column(name = "shipping_recipient_phone", length = 32)
    private String recipientPhone;
}
