package com.example.shop.promo;

import com.example.shop.dto.request.PromoCodeRequest;
import com.example.shop.exception.*;
import com.example.shop.model.enums.DiscountType;
import com.example.shop.service.PromoCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seed promos: WELCOME10 (10%, min 50, cap 100, 1 per user - Aida already used it on ORD-2026-0003),
 * SAVE20 (20 off, min 200, 2 per user), OLDSALE (expired).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PromoCodeTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PromoCodeService service;
    @Autowired jakarta.persistence.EntityManager em;

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private String login(String email, String pw) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    /** A brand-new customer, so no promo usage history. */
    private String fresh() throws Exception {
        String email = "promo-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Pat\",\"lastName\":\"Promo\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON);
    }

    private void add(String t, String sku, int qty) throws Exception {
        mvc.perform(as(post("/api/cart/items"), t).content("{\"productId\":" + pid(sku) + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private void apply(String t, String code) throws Exception {
        mvc.perform(as(post("/api/cart/apply-promo"), t).content("{\"code\":\"" + code + "\"}")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions applyExpect(String t, String code, int status) throws Exception {
        return mvc.perform(as(post("/api/cart/apply-promo"), t).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().is(status));
    }

    // ---------------------------------------------------------------- percentage / fixed

    @Test
    void percentageDiscountAppliesToTheCartTotal() throws Exception {
        String t = fresh();
        add(t, "AP20W", 3); // 57.00
        mvc.perform(as(post("/api/cart/apply-promo"), t).content("{\"code\":\"welcome10\"}")) // case-insensitive
                .andExpect(status().isOk()).andExpect(jsonPath("$.appliedPromoCode").value("WELCOME10"))
                .andExpect(jsonPath("$.subtotal").value(57.0)).andExpect(jsonPath("$.discount").value(5.7))
                .andExpect(jsonPath("$.total").value(51.3)).andExpect(jsonPath("$.promoWarning").doesNotExist());
    }

    @Test
    void percentageIsCappedAtMaxDiscountAmount() throws Exception {
        String t = fresh();
        add(t, "MBA13", 1); // 1099 -> 10% = 109.90, cap 100
        apply(t, "WELCOME10");
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.discount").value(100.0))
                .andExpect(jsonPath("$.total").value(999.0));
    }

    @Test
    void fixedAmountDiscount() throws Exception {
        String t = fresh();
        add(t, "IPH15", 1); // 749 >= 200
        apply(t, "SAVE20");
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.discount").value(20.0)).andExpect(jsonPath("$.total").value(729.0));
    }

    @Test
    void fixedDiscountNeverExceedsTheEligibleItems() throws Exception {
        String t = fresh();
        service.create(adminId(), req("BIG500", DiscountType.FIXED_AMOUNT, "500", null, null, Set.of(), Set.of(pid("AP20W"))));
        add(t, "AP20W", 1); // 19
        add(t, "MBA13", 1); // 1099, not eligible
        apply(t, "BIG500");
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.discount").value(19.0)); // not 500
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void refusedBelowTheMinimumOrderAmount() throws Exception {
        String t = fresh();
        add(t, "AP20W", 1); // 19 < 50
        applyExpect(t, "WELCOME10", 400).andExpect(jsonPath("$.message", containsString("at least 50")));
    }

    @Test
    void refusedWhenExpiredNotYetValidUnknownOrInactive() throws Exception {
        String t = fresh();
        add(t, "IPH15", 1);
        applyExpect(t, "OLDSALE", 400).andExpect(jsonPath("$.message").value("Promo code has expired"));
        jdbc.update("update promo_codes set valid_from = now() + interval '2 days' where code = 'SAVE20'");
        applyExpect(t, "SAVE20", 400).andExpect(jsonPath("$.message").value("Promo code is not valid yet"));
        applyExpect(t, "NOSUCHCODE", 400).andExpect(jsonPath("$.message").value("Promo code is not valid"));
        jdbc.update("update promo_codes set is_active = false where code = 'WELCOME10'");
        applyExpect(t, "WELCOME10", 400).andExpect(jsonPath("$.message").value("Promo code is not valid")); // same text as unknown
    }

    @Test
    void refusedWhenFullyUsed() throws Exception {
        String t = fresh();
        add(t, "IPH15", 1);
        jdbc.update("update promo_codes set max_usage_count = current_usage_count where code = 'SAVE20'");
        applyExpect(t, "SAVE20", 400).andExpect(jsonPath("$.message", containsString("fully used")));
    }

    @Test
    void refusedWhenThisCustomerUsedItTooOften() throws Exception {
        String aida = login("customer@shop.local", "Customer123!"); // used WELCOME10 on ORD-2026-0003, limit 1
        add(aida, "IPH15", 1);
        applyExpect(aida, "WELCOME10", 400).andExpect(jsonPath("$.message", containsString("maximum number")));
        apply(aida, "SAVE20"); // a different code is fine
    }

    @Test
    void emptyCartAndAnonymousAreRefused() throws Exception {
        applyExpect(fresh(), "WELCOME10", 400).andExpect(jsonPath("$.message").value("Your cart is empty"));
        mvc.perform(post("/api/cart/apply-promo").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"SAVE20\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/promo/validate").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"SAVE20\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------ applicable products

    @Test
    void categoryRestrictionOnlyDiscountsMatchingLinesAndIncludesSubcategories() throws Exception {
        String t = fresh();
        Long phones = jdbc.queryForObject("select id from categories where slug = 'phones'", Long.class);
        service.create(adminId(), req("PHONES15", DiscountType.PERCENTAGE, "15", null, null, Set.of(phones), Set.of()));
        add(t, "AP20W", 1); // accessories: not eligible
        applyExpect(t, "PHONES15", 400).andExpect(jsonPath("$.message", containsString("does not apply")));
        add(t, "IPH15", 1); // a Smartphones product, a child of Phones: eligible, 749 * 15% = 112.35
        apply(t, "PHONES15");
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.discount").value(112.35));
    }

    @Test
    void freeShippingCodeSetsTheFlagInsteadOfADiscount() throws Exception {
        String t = fresh();
        service.create(adminId(), req("SHIPFREE", DiscountType.FREE_SHIPPING, "0", null, null, Set.of(), Set.of()));
        add(t, "AP20W", 1);
        apply(t, "SHIPFREE");
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.freeShipping").value(true))
                .andExpect(jsonPath("$.discount").value(0)).andExpect(jsonPath("$.total").value(19.0));
    }

    // ------------------------------------------------- re-checked on every view

    @Test
    void aCodeThatStopsWorkingStopsDiscountingAndExplainsWhy() throws Exception {
        String t = fresh();
        add(t, "IPH15", 1);
        apply(t, "SAVE20");
        jdbc.update("update promo_codes set valid_to = now() - interval '1 minute' where code = 'SAVE20'");
        em.clear();
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.total").value(749.0)).andExpect(jsonPath("$.appliedPromoCode").value("SAVE20"))
                .andExpect(jsonPath("$.promoWarning").value("Promo code has expired"));
        mvc.perform(as(delete("/api/cart/promo"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedPromoCode").doesNotExist()).andExpect(jsonPath("$.promoWarning").doesNotExist());
    }

    @Test
    void droppingBelowTheMinimumAfterApplyingWarnsInsteadOfDiscounting() throws Exception {
        String t = fresh();
        add(t, "AP20W", 3); // 57 >= 50
        apply(t, "WELCOME10");
        long itemId = json.readTree(mvc.perform(as(get("/api/cart"), t)).andReturn().getResponse().getContentAsString())
                .get("items").get(0).get("id").asLong();
        mvc.perform(as(put("/api/cart/items/" + itemId), t).content("{\"quantity\":1}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.discount").value(0)).andExpect(jsonPath("$.promoWarning", containsString("at least 50")));
    }

    // ----------------------------------------------------------- validate endpoint

    @Test
    void validateWithAnAmount() throws Exception {
        String t = fresh();
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"WELCOME10\",\"orderAmount\":200}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("WELCOME10"))
                .andExpect(jsonPath("$.discountType").value("PERCENTAGE")).andExpect(jsonPath("$.discountAmount").value(20.0));
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"WELCOME10\",\"orderAmount\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"OLDSALE\",\"orderAmount\":200}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Promo code has expired"));
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists());
    }

    @Test
    void validateWithoutAnAmountUsesTheCart() throws Exception {
        String t = fresh();
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"SAVE20\"}")).andExpect(status().isBadRequest());
        add(t, "IPH15", 1);
        mvc.perform(as(post("/api/promo/validate"), t).content("{\"code\":\"SAVE20\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.discountAmount").value(20.0));
    }

    @Test
    void restrictedCodesCannotBeValidatedAgainstABareAmount() throws Exception {
        service.create(adminId(), req("ONLYSTRAP", DiscountType.PERCENTAGE, "10", null, null, Set.of(), Set.of(pid("GWSTRAP"))));
        mvc.perform(as(post("/api/promo/validate"), fresh()).content("{\"code\":\"ONLYSTRAP\",\"orderAmount\":100}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("selected products")));
    }

    // ------------------------------------------------------------- usage tracking

    private Long orderId() {
        return jdbc.queryForObject("select id from orders where order_number = 'ORD-2026-0001'", Long.class);
    }

    private Long promoId(String code) {
        return jdbc.queryForObject("select id from promo_codes where code = ?", Long.class, code);
    }

    private Long customerId() {
        return jdbc.queryForObject("select id from users where email = 'customer@shop.local'", Long.class);
    }

    @Test
    void recordingAndReleasingUsage() {
        Long save20 = promoId("SAVE20");
        service.recordUsage(save20, customerId(), orderId(), new BigDecimal("20.00"));
        assertThat(jdbc.queryForObject("select current_usage_count from promo_codes where id = ?", Integer.class, save20)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from promo_code_usages where order_id = ? and promo_code_id = ?", Integer.class, orderId(), save20)).isEqualTo(1);
        service.releaseUsage(orderId());
        em.flush(); // the service's deletes are pending until the transaction commits
        assertThat(jdbc.queryForObject("select current_usage_count from promo_codes where id = ?", Integer.class, save20)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from promo_code_usages where order_id = ? and promo_code_id = ?", Integer.class, orderId(), save20)).isZero();
    }

    @Test
    void theLastUseCanOnlyBeTakenOnce() {
        Long save20 = promoId("SAVE20");
        jdbc.update("update promo_codes set max_usage_count = 1, current_usage_count = 0 where id = ?", save20);
        service.recordUsage(save20, customerId(), orderId(), new BigDecimal("20"));
        assertThatThrownBy(() -> service.recordUsage(save20, customerId(), orderId(), new BigDecimal("20")))
                .isInstanceOf(PromoCodeInvalidException.class);
    }

    // ------------------------------------------------------------------ staff

    private Long adminId() {
        return jdbc.queryForObject("select id from users where email = 'admin@shop.local'", Long.class);
    }

    private PromoCodeRequest req(String code, DiscountType type, String value, LocalDateTime from, LocalDateTime to,
                                 Set<Long> categories, Set<Long> products) {
        return new PromoCodeRequest(code, "test", type, new BigDecimal(value), null, null, null, null, from, to, true,
                categories, products);
    }

    @Test
    void createStoresUppercaseAndRefusesDuplicatesAnyCase() {
        var created = service.create(adminId(), req(" summer5 ", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of()));
        assertThat(created.code()).isEqualTo("SUMMER5");
        assertThatThrownBy(() -> service.create(adminId(), req("Summer5", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of())))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> service.create(adminId(), req("welcome10", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of())))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createValidatesTheRules() {
        assertThatThrownBy(() -> service.create(adminId(), req("P101", DiscountType.PERCENTAGE, "101", null, null, Set.of(), Set.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(adminId(), req("P0", DiscountType.PERCENTAGE, "0", null, null, Set.of(), Set.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(adminId(), req("F0", DiscountType.FIXED_AMOUNT, "0", null, null, Set.of(), Set.of())))
                .isInstanceOf(BadRequestException.class);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> service.create(adminId(), req("BADDATES", DiscountType.PERCENTAGE, "5", now, now.minusDays(1), Set.of(), Set.of())))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.create(adminId(), req("NOCAT", DiscountType.PERCENTAGE, "5", null, null, Set.of(999999L), Set.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateChangesRulesAndTargets() {
        var c = service.create(adminId(), req("TEMP", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of(pid("GWSTRAP"))));
        assertThat(c.applicableProductIds()).containsExactly(pid("GWSTRAP"));
        var u = service.update(c.id(), req("TEMP", DiscountType.PERCENTAGE, "8", null, null, Set.of(), Set.of()));
        assertThat(u.discountValue()).isEqualByComparingTo("8");
        assertThat(u.applicableProductIds()).isEmpty();
        assertThatThrownBy(() -> service.update(c.id(), req("SAVE20", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of())))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> service.update(999999L, req("X", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesUnusedCodesButOnlyDeactivatesUsedOnes() {
        var unused = service.create(adminId(), req("GONE", DiscountType.PERCENTAGE, "5", null, null, Set.of(), Set.of()));
        service.delete(unused.id());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from promo_codes where code = 'GONE'", Integer.class)).isZero();
        service.delete(promoId("WELCOME10")); // has a recorded use on ORD-2026-0003
        em.flush();
        assertThat(jdbc.queryForObject("select is_active from promo_codes where code = 'WELCOME10'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from promo_code_usages", Integer.class)).isEqualTo(1); // history kept
    }

    @Test
    void listIsPagedNewestFirst() {
        var page = service.list(org.springframework.data.domain.PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting("code").containsExactly("OLDSALE", "SAVE20");
    }
}
