package com.example.shop.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aida's seeded ORD-2026-0001 is PENDING (total 799.00) and ORD-2026-0002 is already PAID.
 * The simulated bank's delay is switched off here; the cards decide the outcome.
 */
@SpringBootTest(properties = {"shop.payment.delay-min-ms=0", "shop.payment.delay-max-ms=0"})
@AutoConfigureMockMvc
@Transactional
class PaymentApiTest {
    private static final String OK_CARD = "4111111111111111";
    private static final String FUNDS_CARD = "4000000000000002";
    private static final String NETWORK_CARD = "4000000000000069";
    private static final String FRAUD_CARD = "4000000000000077";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private String token(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String aida() throws Exception { return token("customer@shop.local", "Customer123!"); }

    private Long orderId(String number) {
        return jdbc.queryForObject("select id from orders where order_number = ?", Long.class, number);
    }

    private Long pending() { return orderId("ORD-2026-0001"); }

    private String orderStatus(Long id) {
        em.flush();
        return jdbc.queryForObject("select status from orders where id = ?", String.class, id);
    }

    private String paymentColumn(Long orderId, String column) {
        em.flush();
        return jdbc.queryForObject("select " + column + "::text from payments where order_id = ?", String.class, orderId);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions payCard(String t, Long orderId, String card) throws Exception {
        return mvc.perform(as(post("/api/payments/process"), t).content("{\"orderId\":" + orderId
                + ",\"paymentMethod\":\"CARD\",\"cardNumber\":\"" + card + "\",\"expiry\":\"12/45\",\"cvv\":\"123\"}"));
    }

    private ResultActions payWith(String t, Long orderId, String method) throws Exception {
        return mvc.perform(as(post("/api/payments/process"), t).content("{\"orderId\":" + orderId + ",\"paymentMethod\":\"" + method + "\"}"));
    }

    // ------------------------------------------------------- the five scenarios

    @Test
    void scenario1_theAlwaysSucceedsCardPaysTheOrder() throws Exception {
        Long id = pending();
        String raw = payCard(aida(), id, OK_CARD).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(799.0))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.cardLastFourDigits").value("1111"))
                .andExpect(jsonPath("$.transactionId", startsWith("TXN-")))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.errorMessage").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain(OK_CARD).doesNotContain("cvv").doesNotContain("123\"");
        assertThat(orderStatus(id)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select paid_at is not null from orders where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void scenario2_insufficientFundsIsDeclinedAndTheOrderStaysConfirmed() throws Exception {
        Long id = pending();
        payCard(aida(), id, FUNDS_CARD).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402)).andExpect(jsonPath("$.error").value("PAYMENT_REQUIRED"))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("insufficient funds")))
                .andExpect(jsonPath("$.details").isEmpty());
        assertThat(orderStatus(id)).isEqualTo("CONFIRMED"); // never PAID
        assertThat(paymentColumn(id, "status")).isEqualTo("FAILED");
        assertThat(paymentColumn(id, "error_code")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(paymentColumn(id, "card_last_four_digits")).isEqualTo("0002");
        assertThat(paymentColumn(id, "paid_at")).isNull();
    }

    @Test
    void scenario3_aNetworkErrorLeavesTheOrderConfirmed() throws Exception {
        Long id = pending();
        payCard(aida(), id, NETWORK_CARD).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("network")));
        assertThat(orderStatus(id)).isEqualTo("CONFIRMED");
        assertThat(paymentColumn(id, "error_code")).isEqualTo("NETWORK_ERROR");
    }

    @Test
    void scenario4_suspectedFraudIsDeclined() throws Exception {
        Long id = pending();
        payCard(aida(), id, FRAUD_CARD).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("fraud")));
        assertThat(orderStatus(id)).isEqualTo("CONFIRMED");
        assertThat(paymentColumn(id, "error_code")).isEqualTo("SUSPECTED_FRAUD");
    }

    @Test
    void scenario5_anyOtherCardEitherWayKeepsOrderAndPaymentConsistent() throws Exception {
        Long id = pending();
        int status = payCard(aida(), id, "4242424242424242").andReturn().getResponse().getStatus();
        if (status == 200) {
            assertThat(orderStatus(id)).isEqualTo("PAID");
            assertThat(paymentColumn(id, "status")).isEqualTo("COMPLETED");
        } else {
            assertThat(status).isEqualTo(402);
            assertThat(orderStatus(id)).isEqualTo("CONFIRMED");
            assertThat(paymentColumn(id, "status")).isEqualTo("FAILED");
            assertThat(paymentColumn(id, "error_code")).isEqualTo("PROCESSING_ERROR");
        }
    }

    // ------------------------------------------------------------------ retries

    @Test
    void afterAFailureTheCustomerCanRetryOnTheSameOrder() throws Exception {
        String t = aida();
        Long id = pending();
        payCard(t, id, FUNDS_CARD).andExpect(status().isPaymentRequired());
        String failedTxn = paymentColumn(id, "transaction_id");
        payCard(t, id, OK_CARD).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(orderStatus(id)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(paymentColumn(id, "transaction_id")).isNotEqualTo(failedTxn);
        assertThat(paymentColumn(id, "error_code")).isNull(); // the old failure is cleared
    }

    @Test
    void anOrderCannotBePaidTwice() throws Exception {
        String t = aida();
        Long id = pending();
        payCard(t, id, OK_CARD).andExpect(status().isOk());
        payCard(t, id, OK_CARD).andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("already paid")));
        payCard(t, orderId("ORD-2026-0002"), OK_CARD).andExpect(status().isConflict()); // seeded as PAID
    }

    // --------------------------------------------------------------- validation

    @Test
    void anInvalidCardNumberChangesNothing() throws Exception {
        Long id = pending();
        payCard(aida(), id, "4111111111111112").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Card number is not valid"));
        assertThat(orderStatus(id)).isEqualTo("PENDING"); // not even confirmed
        assertThat(jdbc.queryForObject("select count(*) from payments where order_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void cardPaymentsNeedAllCardFieldsAndAnUnexpiredCard() throws Exception {
        String t = aida();
        Long id = pending();
        payWith(t, id, "CARD").andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/payments/process"), t).content("{\"orderId\":" + id
                + ",\"paymentMethod\":\"CARD\",\"cardNumber\":\"" + OK_CARD + "\",\"expiry\":\"01/20\",\"cvv\":\"123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Card has expired"));
        mvc.perform(as(post("/api/payments/process"), t).content("{\"orderId\":" + id
                + ",\"paymentMethod\":\"CARD\",\"cardNumber\":\"" + OK_CARD + "\",\"expiry\":\"13/45\",\"cvv\":\"12\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.expiry").exists()).andExpect(jsonPath("$.details.cvv").exists());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
    }

    @Test
    void theClientCannotChooseTheAmount() throws Exception {
        Long id = pending();
        mvc.perform(as(post("/api/payments/process"), aida()).content("{\"orderId\":" + id + ",\"paymentMethod\":\"CARD\","
                        + "\"cardNumber\":\"" + OK_CARD + "\",\"expiry\":\"12/45\",\"cvv\":\"123\",\"amount\":0.01,\"status\":\"FAILED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(799.0)).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ------------------------------------------------------ ownership and state

    @Test
    void onlyTheOwnerCanPayAndNeverAnonymously() throws Exception {
        Long id = pending();
        String stranger = registerStranger();
        payCard(stranger, id, OK_CARD).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/payments/" + id + "/status"), stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/api/payments/process").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + id + ",\"paymentMethod\":\"CARD\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/payments/" + id + "/status")).andExpect(status().isUnauthorized());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
    }

    private String registerStranger() throws Exception {
        String email = "payer-" + java.util.UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Pay\",\"lastName\":\"Er\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    @Test
    void cancelledAndShippedOrdersCannotBePaid() throws Exception {
        String t = aida();
        payCard(t, orderId("ORD-2026-0005"), OK_CARD).andExpect(status().isConflict());   // CANCELLED
        payCard(t, orderId("ORD-2026-0003"), OK_CARD).andExpect(status().isConflict());   // SHIPPED
        payCard(t, 999999L, OK_CARD).andExpect(status().isNotFound());
    }

    @Test
    void aPaymentInProgressBlocksASecondOneAndACancel() throws Exception {
        String t = aida();
        Long id = pending();
        payCard(t, id, NETWORK_CARD).andExpect(status().isPaymentRequired()); // leaves a payment row behind
        jdbc.update("update payments set status = 'PROCESSING', metadata = ?::jsonb where order_id = ?",
                "{\"startedAt\":\"" + java.time.Instant.now() + "\"}", id);
        em.clear();
        payCard(t, id, OK_CARD).andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("in progress")));
        mvc.perform(as(put("/api/orders/" + id + "/cancel"), t)).andExpect(status().isConflict());
        // an abandoned attempt (the server died mid-payment) must not lock the customer out forever
        jdbc.update("update payments set metadata = ?::jsonb where order_id = ?",
                "{\"startedAt\":\"" + java.time.Instant.now().minusSeconds(600) + "\"}", id);
        em.clear();
        payCard(t, id, OK_CARD).andExpect(status().isOk());
    }

    @Test
    void aFailedPaymentOrderCanStillBeCancelledAndStockComesBack() throws Exception {
        String t = aida();
        Long id = pending();
        payCard(t, id, FUNDS_CARD).andExpect(status().isPaymentRequired());
        mvc.perform(as(put("/api/orders/" + id + "/cancel"), t)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select reserved_quantity from products where sku = 'GS24'", Integer.class)).isZero();
    }

    // ------------------------------------------------------- other methods

    @Test
    void cashOnDeliveryConfirmsTheOrderAndIsPaidWhenStaffSayCashWasCollected() throws Exception {
        Long id = pending();
        payWith(aida(), id, "CASH_ON_DELIVERY").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.cardLastFourDigits").doesNotExist());
        assertThat(orderStatus(id)).isEqualTo("CONFIRMED");
    }

    @Test
    void paypalWorksAndBonusPointsAreRefused() throws Exception {
        Long id = pending();
        payWith(aida(), id, "BONUS_POINTS").andExpect(status().isBadRequest());
        assertThat(orderStatus(id)).isEqualTo("PENDING");
        payWith(aida(), id, "PAYPAL").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(orderStatus(id)).isEqualTo("PAID");
    }

    // ------------------------------------------------------------------ reads

    @Test
    void statusShowsTheOrderAndPaymentTogether() throws Exception {
        String t = aida();
        Long id = pending();
        mvc.perform(as(get("/api/payments/" + id + "/status"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("PENDING")).andExpect(jsonPath("$.paymentStatus").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist());
        payCard(t, id, FUNDS_CARD).andExpect(status().isPaymentRequired());
        mvc.perform(as(get("/api/payments/" + id + "/status"), t)).andExpect(jsonPath("$.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("FAILED")).andExpect(jsonPath("$.payment.errorMessage").exists());
        payCard(t, id, OK_CARD).andExpect(status().isOk());
        mvc.perform(as(get("/api/payments/" + id + "/status"), t)).andExpect(jsonPath("$.orderStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED")).andExpect(jsonPath("$.payment.cardLastFourDigits").value("1111"));
    }

    @Test
    void methodsArePublicAndLeaveOutBonusPoints() throws Exception {
        mvc.perform(get("/api/payments/methods")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].method", containsInAnyOrder("CARD", "PAYPAL", "CASH_ON_DELIVERY")));
    }

    @Test
    void noFullCardNumberOrCvvIsStoredAnywhere() throws Exception {
        Long id = pending();
        payCard(aida(), id, FUNDS_CARD).andExpect(status().isPaymentRequired());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name = 'payments' "
                + "and (column_name ilike '%card_number%' or column_name ilike '%cvv%' or column_name ilike '%pan%')", Integer.class)).isZero();
        String row = jdbc.queryForObject("select row_to_json(p)::text from payments p where order_id = ?", String.class, id);
        // only the card number is checked as text: a short string like the CVV would match timestamps and random ids by chance
        assertThat(row).doesNotContain(FUNDS_CARD);
        assertThat(jdbc.queryForObject("select length(card_last_four_digits) from payments where order_id = ?", Integer.class, id)).isEqualTo(4);
    }
}
