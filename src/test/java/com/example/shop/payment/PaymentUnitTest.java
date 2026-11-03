package com.example.shop.payment;

import com.example.shop.model.enums.PaymentMethod;
import com.example.shop.service.payment.PaymentGateway;
import com.example.shop.service.payment.SimulatedPaymentGateway;
import com.example.shop.util.LuhnValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentUnitTest {

    // ------------------------------------------------------------------ Luhn

    @Test
    void luhnAcceptsRealTestCardNumbers() {
        for (String ok : new String[]{"4111111111111111", "4000000000000002", "4000000000000069", "4000000000000077",
                "5555555555554444", "378282246310005", "4242424242424242"}) {
            assertThat(LuhnValidator.isValid(ok)).as(ok).isTrue();
        }
    }

    @Test
    void luhnRejectsTyposAndGarbage() {
        for (String bad : new String[]{"4111111111111112", "4111111111111110", "1234567812345678", "", "abcd",
                "4111 1111 1111 1111", "411111111111", "41111111111111111111", "4111-1111-1111-1111"}) {
            assertThat(LuhnValidator.isValid(bad)).as(bad).isFalse();
        }
        assertThat(LuhnValidator.isValid(null)).isFalse();
    }

    @Test
    void luhnCatchesEverySingleDigitTypo() {
        String good = "4111111111111111";
        for (int i = 0; i < good.length(); i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == good.charAt(i)) continue;
                String typo = good.substring(0, i) + d + good.substring(i + 1);
                assertThat(LuhnValidator.isValid(typo)).as(typo).isFalse();
            }
        }
    }

    // --------------------------------------------------------------- gateway

    private static PaymentGateway.Result charge(SimulatedPaymentGateway g, String card) {
        return g.charge(new PaymentGateway.Charge(PaymentMethod.CARD, card, new BigDecimal("10.00")));
    }

    private static SimulatedPaymentGateway fast(RandomGenerator random) {
        return new SimulatedPaymentGateway(random, 0, 0);
    }

    /** A generator that always answers the same number: 0.99 would fail the 90% roll, 0.0 would pass it. */
    private static RandomGenerator always(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    void theFourMagicCardsIgnoreTheDiceEntirely() {
        var unluckyDice = fast(always(0.99));
        var luckyDice = fast(always(0.0));
        for (var g : new SimulatedPaymentGateway[]{unluckyDice, luckyDice}) {
            assertThat(charge(g, "4111111111111111").success()).isTrue();
            var funds = charge(g, "4000000000000002");
            assertThat(funds.success()).isFalse();
            assertThat(funds.errorCode()).isEqualTo("INSUFFICIENT_FUNDS");
            assertThat(funds.errorMessage()).containsIgnoringCase("insufficient funds");
            assertThat(charge(g, "4000000000000069").errorCode()).isEqualTo("NETWORK_ERROR");
            assertThat(charge(g, "4000000000000077").errorCode()).isEqualTo("SUSPECTED_FRAUD");
        }
    }

    @Test
    void anyOtherCardFollowsTheDice() {
        assertThat(charge(fast(always(0.0)), "4242424242424242").success()).isTrue();
        assertThat(charge(fast(always(0.899)), "4242424242424242").success()).isTrue();
        var failed = charge(fast(always(0.9)), "4242424242424242");
        assertThat(failed.success()).isFalse();
        assertThat(failed.errorCode()).isEqualTo("PROCESSING_ERROR");
    }

    @Test
    void roughlyNinetyPercentOfOtherCardsSucceed() {
        var g = fast(new Random(42));
        int ok = 0;
        int runs = 20_000;
        for (int i = 0; i < runs; i++) if (charge(g, "4242424242424242").success()) ok++;
        assertThat(ok / (double) runs).isBetween(0.88, 0.92);
    }

    @Test
    void theBankTakesBetweenTheConfiguredMinAndMax() {
        var g = new SimulatedPaymentGateway(new Random(1), 60, 120);
        for (int i = 0; i < 3; i++) {
            long start = System.nanoTime();
            charge(g, "4111111111111111");
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertThat(ms).isBetween(55L, 400L);
        }
    }

    @Test
    void theChargeNeverPrintsTheCardNumber() {
        var c = new PaymentGateway.Charge(PaymentMethod.CARD, "4111111111111111", new BigDecimal("5"));
        assertThat(c.toString()).doesNotContain("4111");
    }

    @Test
    void paypalAlwaysWorksInTheSimulation() {
        var g = fast(always(0.99));
        assertThat(g.charge(new PaymentGateway.Charge(PaymentMethod.PAYPAL, null, BigDecimal.TEN)).success()).isTrue();
    }
}
