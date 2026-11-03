package com.example.shop.dto;

import com.example.shop.dto.request.AddCartItemRequest;
import com.example.shop.dto.request.CreateProductRequest;
import com.example.shop.dto.request.ProcessPaymentRequest;
import com.example.shop.dto.request.RegisterRequest;
import com.example.shop.dto.request.ReviewRequest;
import com.example.shop.model.enums.PaymentMethod;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RequestValidationTest {

    @Autowired
    Validator validator;

    private static Set<String> fields(Set<? extends ConstraintViolation<?>> v) {
        return v.stream().map(c -> c.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void validRegistrationPasses() {
        var r = new RegisterRequest("new.user@example.com", "Str0ngPass", "New", "User", null);
        assertThat(validator.validate(r)).isEmpty();
    }

    @Test
    void duplicateEmailIsRefused() {
        // customer@shop.local is in the seed data
        var r = new RegisterRequest("customer@shop.local", "Str0ngPass", "A", "B", null);
        assertThat(fields(validator.validate(r))).containsExactly("email");
    }

    @Test
    void weakPasswordsAreRefused() {
        for (String pw : new String[]{"short1A", "alllowercase1", "ALLUPPERCASE1", "NoDigitsHere"}) {
            var r = new RegisterRequest("x" + pw + "@example.com", pw, "A", "B", null);
            assertThat(fields(validator.validate(r))).as(pw).containsExactly("password");
        }
    }

    @Test
    void blankAndBadEmailNameTheField() {
        var r = new RegisterRequest("not-an-email", "Str0ngPass", "", "B", null);
        assertThat(fields(validator.validate(r))).containsExactlyInAnyOrder("email", "firstName");
    }

    @Test
    void productNeedsPositivePriceAndCategory() {
        var r = new CreateProductRequest("Phone", null, null, "SKU-1", new BigDecimal("-1"), null, null, null,
                5, null, null, null, null, null, null, null);
        assertThat(fields(validator.validate(r))).containsExactlyInAnyOrder("basePrice", "categoryId");
    }

    @Test
    void reviewRatingMustBeOneToFive() {
        assertThat(fields(validator.validate(new ReviewRequest(0, null, null, null, null)))).containsExactly("rating");
        assertThat(fields(validator.validate(new ReviewRequest(6, null, null, null, null)))).containsExactly("rating");
        assertThat(validator.validate(new ReviewRequest(5, "Great", null, null, null))).isEmpty();
    }

    @Test
    void cartQuantityMustBePositive() {
        assertThat(fields(validator.validate(new AddCartItemRequest(1L, null, 0)))).containsExactly("quantity");
    }

    @Test
    void cardNumberFormatIsChecked() {
        var bad = new ProcessPaymentRequest(1L, PaymentMethod.CARD, "4111-abc", "13/99", "1");
        assertThat(fields(validator.validate(bad))).containsExactlyInAnyOrder("cardNumber", "expiry", "cvv");
        var ok = new ProcessPaymentRequest(1L, PaymentMethod.CARD, "4111111111111111", "12/30", "123");
        assertThat(validator.validate(ok)).isEmpty();
        assertThat(ok.toString()).doesNotContain("4111");
    }
}
