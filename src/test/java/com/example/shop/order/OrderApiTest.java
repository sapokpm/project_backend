package com.example.shop.order;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Customer order endpoints against the seeded data. Seed prices: IPH15 749 (discount running), AP20W 19, GWSTRAP 25,
 * MBA13 1099. Delivery: COURIER 5 (free from 100), PICKUP_POINT 2 (free from 50), EXPRESS 15 (never free).
 * Aida owns all five seeded orders; ORD-2026-0001 is PENDING and holds a reservation of 1 Galaxy S24.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private int reserved(String sku) {
        return jdbc.queryForObject("select reserved_quantity from products where sku = ?", Integer.class, sku);
    }

    private Long orderIdOf(String number) {
        return jdbc.queryForObject("select id from orders where order_number = ?", Long.class, number);
    }

    private String login(String email, String pw) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String aida() throws Exception { return login("customer@shop.local", "Customer123!"); }

    private Long aidaAddress() {
        return jdbc.queryForObject("select a.id from addresses a join users u on u.id = a.user_id where u.email = 'customer@shop.local'", Long.class);
    }

    /** A new customer who has an address of their own. */
    private record Buyer(String token, Long addressId) {
    }

    private Buyer buyer() throws Exception {
        String email = "buyer-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Bo\",\"lastName\":\"Buyer\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long uid = jdbc.queryForObject("select id from users where email = ?", Long.class, email);
        jdbc.update("insert into addresses (title, country, city, street, recipient_name, recipient_phone, is_default, user_id) "
                + "values ('Home','Kazakhstan','Astana','Main st 1','Bo Buyer','+77000000000',true,?)", uid);
        Long addr = jdbc.queryForObject("select id from addresses where user_id = ?", Long.class, uid);
        return new Buyer(json.readTree(body).get("accessToken").asText(), addr);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private void addToCart(String token, String sku, int qty) throws Exception {
        mvc.perform(as(post("/api/cart/items"), token).content("{\"productId\":" + pid(sku) + ",\"quantity\":" + qty + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions order(String token, Long addressId, String method) throws Exception {
        return mvc.perform(as(post("/api/orders"), token)
                .content("{\"addressId\":" + addressId + ",\"deliveryMethod\":\"" + method + "\"}"));
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ create

    @Test
    void ordersTurnTheCartIntoAnOrderWithTheDocumentedTotal() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "IPH15", 1);   // 749.00
        addToCart(b.token(), "AP20W", 2);   // 38.00
        // subtotal 787.00, COURIER is free from 100, no promo
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/orders/\\d+")))
                .andExpect(jsonPath("$.orderNumber", matchesPattern("ORD-\\d{4}-\\d{4,}")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(787.0)).andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.shippingCost").value(0)).andExpect(jsonPath("$.totalAmount").value(787.0))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productName=='iPhone 15')].priceAtPurchase", contains(749.0)))
                .andExpect(jsonPath("$.items[?(@.productName=='Apple 20W USB-C Charger')].lineTotal", contains(38.0)))
                .andExpect(jsonPath("$.shippingAddress.city").value("Astana"))
                .andExpect(jsonPath("$.estimatedDeliveryDate").exists())
                .andExpect(jsonPath("$.adminNote").doesNotExist());
    }

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }

    @Test
    void shippingIsChargedBelowTheFreeThresholdAndExpressIsNeverFree() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "AP20W", 1); // 19.00: below COURIER's 100
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingCost").value(5.0)).andExpect(jsonPath("$.totalAmount").value(24.0));
        addToCart(b.token(), "MBA13", 1); // 1099.00: far above every threshold, but EXPRESS has none
        order(b.token(), b.addressId(), "EXPRESS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingCost").value(15.0)).andExpect(jsonPath("$.totalAmount").value(1114.0));
    }

    @Test
    void shippingThresholdIsComparedWithTheSubtotalBeforeDiscount() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "MBA13", 1); // 1099
        mvc.perform(as(post("/api/cart/apply-promo"), b.token()).content("{\"code\":\"WELCOME10\"}")).andExpect(status().isOk());
        // discount 100 (10% capped), shipping free because the subtotal 1099 >= 100: total = 1099 - 100 + 0
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(1099.0)).andExpect(jsonPath("$.discount").value(100.0))
                .andExpect(jsonPath("$.shippingCost").value(0)).andExpect(jsonPath("$.totalAmount").value(999.0))
                .andExpect(jsonPath("$.promoCode").value("WELCOME10"));
    }

    @Test
    void creatingReservesStockEmptiesTheCartAndCountsThePromoUse() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "IPH15", 2);
        mvc.perform(as(post("/api/cart/apply-promo"), b.token()).content("{\"code\":\"SAVE20\"}")).andExpect(status().isOk());
        int before = reserved("IPH15");
        Integer usesBefore = jdbc.queryForObject("select current_usage_count from promo_codes where code = 'SAVE20'", Integer.class);

        long id = body(order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.discount").value(20.0)).andExpect(jsonPath("$.totalAmount").value(1478.0))).get("id").asLong();

        assertThat(reserved("IPH15")).isEqualTo(before + 2);
        mvc.perform(as(get("/api/cart"), b.token())).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.appliedPromoCode").doesNotExist());
        assertThat(jdbc.queryForObject("select current_usage_count from promo_codes where code = 'SAVE20'", Integer.class)).isEqualTo(usesBefore + 1);
        assertThat(jdbc.queryForObject("select count(*) from promo_code_usages where order_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aFreeShippingCodeZeroesTheShippingCharge() throws Exception {
        Buyer b = buyer();
        jdbc.update("insert into promo_codes (code, discount_type, discount_value, is_active) values ('SHIPFREE','FREE_SHIPPING',0,true)");
        addToCart(b.token(), "AP20W", 1);
        mvc.perform(as(post("/api/cart/apply-promo"), b.token()).content("{\"code\":\"SHIPFREE\"}")).andExpect(status().isOk());
        order(b.token(), b.addressId(), "EXPRESS").andExpect(status().isCreated())
                .andExpect(jsonPath("$.shippingCost").value(0)).andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.totalAmount").value(19.0));
    }

    @Test
    void thePriceInTheOrderIsTodaysPriceNotWhatTheClientSays() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "AP20W", 1);
        jdbc.update("update products set base_price = 30 where sku = 'AP20W'");
        em.clear();
        mvc.perform(as(post("/api/orders"), b.token()).content("{\"addressId\":" + b.addressId()
                        + ",\"deliveryMethod\":\"PICKUP_POINT\",\"subtotal\":1,\"totalAmount\":1,\"shippingCost\":0,\"status\":\"PAID\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(30.0)).andExpect(jsonPath("$.shippingCost").value(2.0))
                .andExpect(jsonPath("$.totalAmount").value(32.0));
    }

    @Test
    void ordersFreezeTheAddressAndThePriceAtPurchase() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "AP20W", 1);
        long id = body(order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())).get("id").asLong();
        jdbc.update("update addresses set city = 'Moved' where id = ?", b.addressId());
        jdbc.update("update products set base_price = 99 where sku = 'AP20W'");
        em.clear();
        mvc.perform(as(get("/api/orders/" + id), b.token())).andExpect(jsonPath("$.shippingAddress.city").value("Astana"))
                .andExpect(jsonPath("$.items[0].priceAtPurchase").value(19.0)).andExpect(jsonPath("$.subtotal").value(19.0));
        assertThat(jdbc.queryForObject("select product_snapshot->>'name' from order_items where order_id = ?", String.class, id))
                .isEqualTo("Apple 20W USB-C Charger");
    }

    // -------------------------------------------------------------- refusals

    @Test
    void aCartMustExistAndAddressAndMethodMustBeValid() throws Exception {
        Buyer b = buyer();
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty"));
        addToCart(b.token(), "AP20W", 1);
        order(b.token(), aidaAddress(), "COURIER").andExpect(status().isNotFound());           // someone else's address
        order(b.token(), 999999L, "COURIER").andExpect(status().isNotFound());
        order(b.token(), b.addressId(), "TELEPORT").andExpect(status().isBadRequest());         // not a delivery method
        mvc.perform(as(post("/api/orders"), b.token()).content("{\"deliveryMethod\":\"COURIER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.addressId").exists());
        jdbc.update("update delivery_options set is_active = false where code = 'COURIER'");
        em.clear();
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isBadRequest());
    }

    @Test
    void bonusPointsCannotBeSpentWithoutABalance() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "IPH15", 1);
        mvc.perform(as(post("/api/orders"), b.token()).content("{\"addressId\":" + b.addressId()
                        + ",\"deliveryMethod\":\"COURIER\",\"bonusPointsToUse\":5000}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("at most 0")));
    }

    @Test
    void outOfStockStopsTheOrderAndAnInvalidPromoToo() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "GWSTRAP", 3); // stock 3
        jdbc.update("update products set reserved_quantity = 1 where sku = 'GWSTRAP'"); // someone else holds one
        em.clear();
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isConflict());

        Buyer c = buyer();
        addToCart(c.token(), "IPH15", 1);
        mvc.perform(as(post("/api/cart/apply-promo"), c.token()).content("{\"code\":\"SAVE20\"}")).andExpect(status().isOk());
        jdbc.update("update promo_codes set valid_to = now() - interval '1 minute' where code = 'SAVE20'");
        em.clear();
        order(c.token(), c.addressId(), "COURIER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Promo code has expired"));
    }

    @Test
    void inactiveProductsInTheCartAreRefused() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "AP20W", 1);
        jdbc.update("update products set is_active = false where sku = 'AP20W'");
        em.clear();
        order(b.token(), b.addressId(), "COURIER").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no longer available")));
    }

    // ------------------------------------------------------------- ownership

    @Test
    void customersSeeTheirOwnOrdersWithPaymentDetailsMinimal() throws Exception {
        String t = aida();
        mvc.perform(as(get("/api/orders"), t)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-2026-0005")); // newest first
        mvc.perform(as(get("/api/orders/ORD-2026-0003"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED")).andExpect(jsonPath("$.promoCode").value("WELCOME10"))
                .andExpect(jsonPath("$.discount").value(34.9)).andExpect(jsonPath("$.totalAmount").value(314.1))
                .andExpect(jsonPath("$.payment.cardLastFourDigits").value("4242"))
                .andExpect(jsonPath("$.payment.status").value("COMPLETED"));
        mvc.perform(as(get("/api/orders/" + orderIdOf("ORD-2026-0003")), t)).andExpect(jsonPath("$.orderNumber").value("ORD-2026-0003"));
        String raw = mvc.perform(as(get("/api/orders/ORD-2026-0003"), t)).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("customer@shop.local").doesNotContain("adminNote").doesNotContain("password");
    }

    @Test
    void noCustomerCanReadAnotherCustomersOrder() throws Exception {
        Long aidasOrder = orderIdOf("ORD-2026-0002");
        Buyer stranger = buyer();
        mvc.perform(as(get("/api/orders/" + aidasOrder), stranger.token())).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/orders/ORD-2026-0002"), stranger.token())).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/orders/" + aidasOrder + "/cancel"), stranger.token())).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/orders/" + aidasOrder + "/reorder"), stranger.token())).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/orders"), stranger.token())).andExpect(jsonPath("$.totalElements").value(0));
        // the same answer as for an order that does not exist: nothing leaks
        mvc.perform(as(get("/api/orders/999999"), stranger.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/orders/" + aidasOrder)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsAreNotReachableWithACustomerToken() throws Exception {
        mvc.perform(as(get("/api/admin/orders"), aida())).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void customersCancelAPendingOrderAndTheStockComesBack() throws Exception {
        String t = aida();
        Long id = orderIdOf("ORD-2026-0001"); // PENDING, holds 1 Galaxy S24
        assertThat(reserved("GS24")).isEqualTo(1);
        mvc.perform(as(put("/api/orders/" + id + "/cancel"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(reserved("GS24")).isZero();
        mvc.perform(as(put("/api/orders/" + id + "/cancel"), t)).andExpect(status().isConflict()); // not twice
    }

    @Test
    void aCancelledOrderGivesTheStockAndThePromoUseBack() throws Exception {
        Buyer b = buyer();
        addToCart(b.token(), "IPH15", 1);
        mvc.perform(as(post("/api/cart/apply-promo"), b.token()).content("{\"code\":\"SAVE20\"}")).andExpect(status().isOk());
        int reservedBefore = reserved("IPH15");
        Integer usesBefore = jdbc.queryForObject("select current_usage_count from promo_codes where code = 'SAVE20'", Integer.class);
        long id = body(order(b.token(), b.addressId(), "COURIER").andExpect(status().isCreated())).get("id").asLong();
        assertThat(reserved("IPH15")).isEqualTo(reservedBefore + 1);

        mvc.perform(as(put("/api/orders/" + id + "/cancel"), b.token())).andExpect(status().isOk());

        em.flush(); // the usage row is deleted by the service but only written when the transaction commits
        assertThat(reserved("IPH15")).isEqualTo(reservedBefore);
        assertThat(jdbc.queryForObject("select current_usage_count from promo_codes where code = 'SAVE20'", Integer.class)).isEqualTo(usesBefore);
        assertThat(jdbc.queryForObject("select count(*) from promo_code_usages where order_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void customersCannotCancelOnceItIsPaidShippedOrDelivered() throws Exception {
        String t = aida();
        for (String number : new String[]{"ORD-2026-0002", "ORD-2026-0003", "ORD-2026-0004", "ORD-2026-0005"}) {
            mvc.perform(as(put("/api/orders/" + orderIdOf(number) + "/cancel"), t)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("CONFLICT"));
        }
    }

    // ----------------------------------------------------------------- reorder

    @Test
    void reorderPutsTheItemsBackInTheCartAtTodaysPrices() throws Exception {
        String t = aida();
        mvc.perform(as(post("/api/orders/" + orderIdOf("ORD-2026-0004") + "/reorder"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped", hasSize(0))).andExpect(jsonPath("$.cart.items", hasSize(3)))
                .andExpect(jsonPath("$.cart.items[?(@.productName=='iPhone 15')].unitPrice", contains(749.0)));
    }

    @Test
    void reorderSkipsWhatCannotBeBoughtAnymoreInsteadOfFailing() throws Exception {
        String t = aida();
        jdbc.update("update products set is_active = false where sku = 'MAGSAFE'");
        em.clear();
        mvc.perform(as(post("/api/orders/" + orderIdOf("ORD-2026-0004") + "/reorder"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.skipped", hasSize(1))).andExpect(jsonPath("$.cart.items", hasSize(2)));
    }
}
