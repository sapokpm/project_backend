package com.example.shop.service.payment;

import com.example.shop.model.enums.PaymentMethod;

import java.math.BigDecimal;

/** The outside world's payment provider. Only a simulation exists, behind this interface so it can be swapped. */
public interface PaymentGateway {

    /** cardNumber is only for the simulation: it is never stored, logged, or part of toString(). */
    record Charge(PaymentMethod method, String cardNumber, BigDecimal amount) {
        @Override
        public String toString() {
            return "Charge[method=" + method + ", amount=" + amount + "]";
        }
    }

    record Result(boolean success, String errorCode, String errorMessage) {
        public static Result ok() {
            return new Result(true, null, null);
        }

        public static Result declined(String code, String message) {
            return new Result(false, code, message);
        }
    }

    Result charge(Charge charge);
}
