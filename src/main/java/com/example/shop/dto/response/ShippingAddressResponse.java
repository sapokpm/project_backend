package com.example.shop.dto.response;

public record ShippingAddressResponse(String country, String city, String street, String building,
                                      String apartment, String postalCode, String recipientName,
                                      String recipientPhone) {
}
