package com.example.shop.dto.request;

import com.example.shop.model.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// cardNumber, expiry and cvv are used for the simulation only and are never stored or logged
public record ProcessPaymentRequest(
        @NotNull Long orderId,
        @NotNull PaymentMethod paymentMethod,
        @Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits") String cardNumber,
        @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}", message = "Expiry must be MM/YY") String expiry,
        @Pattern(regexp = "\\d{3,4}", message = "CVV must be 3-4 digits") String cvv) {

    // keep the card number out of logs and exception messages
    @Override
    public String toString() {
        return "ProcessPaymentRequest[orderId=" + orderId + ", paymentMethod=" + paymentMethod + "]";
    }
}
