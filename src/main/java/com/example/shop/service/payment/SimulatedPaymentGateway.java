package com.example.shop.service.payment;

import com.example.shop.model.enums.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

/**
 * Pretends to be a bank. Test card numbers decide the outcome:
 * <pre>
 * 4111111111111111  always succeeds
 * 4000000000000002  declined, insufficient funds
 * 4000000000000069  network error
 * 4000000000000077  suspected fraud
 * anything else     90% success, 10% random failure
 * </pre>
 * It waits one to three seconds first, like a real gateway, and never holds a database transaction while it waits.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {
    static final double SUCCESS_RATE = 0.9;

    private final RandomGenerator random;
    private final long minDelayMs;
    private final long maxDelayMs;

    public SimulatedPaymentGateway(RandomGenerator random,
                                   @Value("${shop.payment.delay-min-ms:1000}") long minDelayMs,
                                   @Value("${shop.payment.delay-max-ms:3000}") long maxDelayMs) {
        this.random = random;
        this.minDelayMs = minDelayMs;
        this.maxDelayMs = Math.max(minDelayMs, maxDelayMs);
    }

    @Override
    public Result charge(Charge charge) {
        pause();
        if (charge.method() != PaymentMethod.CARD) {
            return Result.ok(); // PayPal in this simulation always works
        }
        return switch (charge.cardNumber()) {
            case "4111111111111111" -> Result.ok();
            case "4000000000000002" -> Result.declined("INSUFFICIENT_FUNDS", "Card declined: insufficient funds");
            case "4000000000000069" -> Result.declined("NETWORK_ERROR", "Network error while contacting the bank; try again");
            case "4000000000000077" -> Result.declined("SUSPECTED_FRAUD", "Payment declined: suspected fraud");
            default -> random.nextDouble() < SUCCESS_RATE
                    ? Result.ok()
                    : Result.declined("PROCESSING_ERROR", "The payment could not be processed; try again");
        };
    }

    private void pause() {
        if (maxDelayMs <= 0) return;
        long ms = minDelayMs + (long) (random.nextDouble() * (maxDelayMs - minDelayMs));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
