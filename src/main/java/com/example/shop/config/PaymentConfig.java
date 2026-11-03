package com.example.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

@Configuration
public class PaymentConfig {
    /** The randomness behind the payment simulation; a bean so tests can replace it. */
    @Bean
    public RandomGenerator paymentRandom() {
        return new SecureRandom();
    }
}
