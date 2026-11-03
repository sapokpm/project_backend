package com.example.shop.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record Body(@NotBlank String name) {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/t/notfound") void notFound() { throw new ResourceNotFoundException("No product with id 9999"); }
        @GetMapping("/t/unauthorized") void unauthorized() { throw new UnauthorizedException("Invalid token"); }
        @GetMapping("/t/forbidden") void forbidden() { throw new ForbiddenException("Not your order"); }
        @GetMapping("/t/stock") void stock() { throw new InsufficientStockException("Only 2 left"); }
        @GetMapping("/t/payment") void payment() { throw new PaymentFailedException("Card declined"); }
        @GetMapping("/t/cancel") void cancel() { throw new OrderCancellationException("Order already shipped"); }
        @GetMapping("/t/promo") void promo() { throw new PromoCodeInvalidException("Code expired"); }
        @GetMapping("/t/boom") void boom() { throw new IllegalStateException("secret table users_x"); }
        @PostMapping("/t/valid") void valid(@Valid @RequestBody Body b) { }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test void notFound() throws Exception {
        mvc.perform(get("/t/notfound")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No product with id 9999"))
                .andExpect(jsonPath("$.path").value("/t/notfound"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test void unauthorized() throws Exception { mvc.perform(get("/t/unauthorized")).andExpect(status().isUnauthorized()); }
    @Test void forbidden() throws Exception { mvc.perform(get("/t/forbidden")).andExpect(status().isForbidden()); }
    @Test void stock() throws Exception { mvc.perform(get("/t/stock")).andExpect(status().isConflict()); }
    @Test void payment() throws Exception { mvc.perform(get("/t/payment")).andExpect(status().isPaymentRequired()); }
    @Test void cancel() throws Exception { mvc.perform(get("/t/cancel")).andExpect(status().isConflict()); }
    @Test void promo() throws Exception { mvc.perform(get("/t/promo")).andExpect(status().isBadRequest()); }

    @Test void validation() throws Exception {
        mvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test void unreadableBody() throws Exception {
        mvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest());
    }

    @Test void wrongMethod() throws Exception { mvc.perform(post("/t/notfound")).andExpect(status().isMethodNotAllowed()); }

    @Test void unexpectedHidesInternals() throws Exception {
        mvc.perform(get("/t/boom")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
