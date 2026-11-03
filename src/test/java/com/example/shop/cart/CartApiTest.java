package com.example.shop.cart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seed prices: IPH15 base 799 / discount 749 (running), AP20W 19, GWSTRAP 25 with stock 3.
 * Rolled back after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CartApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
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

    private String customer() throws Exception { return login("customer@shop.local", "Customer123!"); }

    private String stranger() throws Exception {
        String email = "cart-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Bo\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private static String item(Long productId, int qty) {
        return "{\"productId\":" + productId + ",\"quantity\":" + qty + "}";
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder guest(MockHttpServletRequestBuilder b, String session) {
        b.contentType(MediaType.APPLICATION_JSON);
        return session == null ? b : b.header("X-Cart-Session", session);
    }

    /** Adds as a guest; returns the session id the server issued. */
    private String guestAdd(String session, Long productId, int qty) throws Exception {
        MvcResult r = mvc.perform(guest(post("/api/cart/items"), session).content(item(productId, qty)))
                .andExpect(status().isCreated()).andReturn();
        return r.getResponse().getHeader("X-Cart-Session");
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------- guests

    @Test
    void anUnknownGuestSeesAnEmptyCartAndNothingIsCreated() throws Exception {
        Integer before = jdbc.queryForObject("select count(*) from carts", Integer.class);
        MvcResult r = mvc.perform(get("/api/cart")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0))).andExpect(jsonPath("$.total").value(0)).andReturn();
        assertThat(r.getResponse().getHeader("X-Cart-Session")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from carts", Integer.class)).isEqualTo(before);
    }

    @Test
    void firstGuestWriteIssuesASessionAndTotalsAreCorrect() throws Exception {
        MvcResult r = mvc.perform(guest(post("/api/cart/items"), null).content(item(pid("IPH15"), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("iPhone 15"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(749.0))
                .andExpect(jsonPath("$.items[0].priceAtAdd").value(749.0))
                .andExpect(jsonPath("$.items[0].priceChanged").value(false))
                .andExpect(jsonPath("$.items[0].lineTotal").value(1498.0))
                .andExpect(jsonPath("$.totalItems").value(2)).andExpect(jsonPath("$.subtotal").value(1498.0))
                .andExpect(jsonPath("$.total").value(1498.0)).andReturn();
        String session = r.getResponse().getHeader("X-Cart-Session");
        assertThat(UUID.fromString(session)).isNotNull();
        mvc.perform(guest(get("/api/cart"), session)).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
        // another guest never sees it
        mvc.perform(guest(get("/api/cart"), UUID.randomUUID().toString())).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void malformedSessionIsRefusedAndTotalsAddAcrossLines() throws Exception {
        mvc.perform(guest(get("/api/cart"), "not-a-uuid")).andExpect(status().isBadRequest());
        String s = guestAdd(null, pid("AP20W"), 2);
        guestAdd(s, pid("GWSTRAP"), 1);
        mvc.perform(guest(get("/api/cart"), s)).andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(3)).andExpect(jsonPath("$.subtotal").value(63.0)); // 2*19 + 25
    }

    @Test
    void addingTheSameProductAgainAddsToTheLine() throws Exception {
        String s = guestAdd(null, pid("AP20W"), 2);
        guestAdd(s, pid("AP20W"), 3);
        mvc.perform(guest(get("/api/cart"), s)).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(5));
    }

    // -------------------------------------------------------------- stock

    @Test
    void stockIsCheckedOnAddAndOnUpdate() throws Exception {
        Long strap = pid("GWSTRAP"); // stock 3
        String s = guestAdd(null, strap, 3);
        mvc.perform(guest(post("/api/cart/items"), s).content(item(strap, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Only 3")));
        long itemId = body(mvc.perform(guest(get("/api/cart"), s)).andReturn()).get("items").get(0).get("id").asLong();
        mvc.perform(guest(put("/api/cart/items/" + itemId), s).content("{\"quantity\":4}")).andExpect(status().isConflict());
        mvc.perform(guest(put("/api/cart/items/" + itemId), s).content("{\"quantity\":2}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void reservedStockIsNotAvailableToTheCart() throws Exception {
        jdbc.update("update products set reserved_quantity = stock_quantity where sku = 'GWSTRAP'");
        mvc.perform(guest(post("/api/cart/items"), null).content(item(pid("GWSTRAP"), 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("out of stock")));
    }

    @Test
    void badQuantitiesAndProductsAreRefused() throws Exception {
        Long p = pid("AP20W");
        mvc.perform(guest(post("/api/cart/items"), null).content(item(p, 0))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.quantity").exists());
        mvc.perform(guest(post("/api/cart/items"), null).content(item(p, 101))).andExpect(status().isBadRequest());
        mvc.perform(guest(post("/api/cart/items"), null).content("{\"quantity\":1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.productId").exists());
        mvc.perform(guest(post("/api/cart/items"), null).content(item(999999L, 1))).andExpect(status().isNotFound());
        jdbc.update("update products set is_active = false where id = ?", p);
        mvc.perform(guest(post("/api/cart/items"), null).content(item(p, 1))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- prices

    @Test
    void cartShowsCurrentPricesAndFlagsAChange() throws Exception {
        Long p = pid("AP20W");
        String s = guestAdd(null, p, 2); // added at 19
        jdbc.update("update products set base_price = 24 where id = ?", p);
        em.clear(); // a real request would start with a fresh persistence context
        mvc.perform(guest(get("/api/cart"), s))
                .andExpect(jsonPath("$.items[0].priceAtAdd").value(19.0))
                .andExpect(jsonPath("$.items[0].unitPrice").value(24.0))
                .andExpect(jsonPath("$.items[0].priceChanged").value(true))
                .andExpect(jsonPath("$.subtotal").value(48.0)); // always the current price
    }

    @Test
    void anInactiveProductInTheCartShowsAsUnavailable() throws Exception {
        Long p = pid("AP20W");
        String s = guestAdd(null, p, 1);
        jdbc.update("update products set is_active = false where id = ?", p);
        em.clear();
        mvc.perform(guest(get("/api/cart"), s)).andExpect(jsonPath("$.items[0].availableQuantity").value(0));
    }

    // ----------------------------------------------------------- variants

    @Test
    void productsWithOptionsNeedAVariant() throws Exception {
        Long p = pid("IPH15");
        jdbc.update("insert into product_variants (sku, name, price, stock_quantity, product_id) values ('IPH15-256','256GB',899,2,?)", p);
        Long v = jdbc.queryForObject("select id from product_variants where sku = 'IPH15-256'", Long.class);
        mvc.perform(guest(post("/api/cart/items"), null).content(item(p, 1))).andExpect(status().isBadRequest());
        mvc.perform(guest(post("/api/cart/items"), null)
                .content("{\"productId\":" + p + ",\"variantId\":999999,\"quantity\":1}")).andExpect(status().isNotFound());
        mvc.perform(guest(post("/api/cart/items"), null)
                        .content("{\"productId\":" + p + ",\"variantId\":" + v + ",\"quantity\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].variantName").value("256GB"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(899.0)).andExpect(jsonPath("$.items[0].availableQuantity").value(2));
        mvc.perform(guest(post("/api/cart/items"), null)
                        .content("{\"productId\":" + p + ",\"variantId\":" + v + ",\"quantity\":3}"))
                .andExpect(status().isConflict()); // variant stock is 2
    }

    // ------------------------------------------------------ edit and clear

    @Test
    void removeAndClear() throws Exception {
        String s = guestAdd(null, pid("AP20W"), 1);
        guestAdd(s, pid("GWSTRAP"), 1);
        JsonNode items = body(mvc.perform(guest(get("/api/cart"), s)).andReturn()).get("items");
        long first = items.get(0).get("id").asLong();
        mvc.perform(guest(delete("/api/cart/items/" + first), s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(guest(delete("/api/cart/items/" + first), s)).andExpect(status().isNotFound());
        mvc.perform(guest(delete("/api/cart"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(0));
    }

    // -------------------------------------------------------- logged in

    @Test
    void loggedInCustomersHaveTheirOwnPersistentCart() throws Exception {
        String t = customer();
        mvc.perform(auth(post("/api/cart/items"), t).content(item(pid("AP20W"), 2))).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/cart"), t)).andExpect(jsonPath("$.items[0].quantity").value(2));
        // a fresh login still sees it
        mvc.perform(auth(get("/api/cart"), customer())).andExpect(jsonPath("$.items", hasSize(1)));
        // the token's owner wins over any session header
        mvc.perform(auth(get("/api/cart"), stranger()).header("X-Cart-Session", UUID.randomUUID().toString()))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void youCannotTouchSomeoneElsesCartItem() throws Exception {
        String t = customer();
        mvc.perform(auth(post("/api/cart/items"), t).content(item(pid("AP20W"), 1))).andExpect(status().isCreated());
        long itemId = body(mvc.perform(auth(get("/api/cart"), t)).andReturn()).get("items").get(0).get("id").asLong();
        String other = stranger();
        mvc.perform(auth(put("/api/cart/items/" + itemId), other).content("{\"quantity\":9}")).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/cart/items/" + itemId), other)).andExpect(status().isNotFound());
        // and a guest cannot reach a user's cart through a session id
        mvc.perform(guest(delete("/api/cart/items/" + itemId), UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/cart"), t)).andExpect(jsonPath("$.items[0].quantity").value(1));
    }

    // -------------------------------------------------------------- merge

    @Test
    void mergeNeedsLogin() throws Exception {
        mvc.perform(post("/api/cart/merge").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + UUID.randomUUID() + "\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void guestCartMergesIntoTheUserCartAndQuantitiesAddUp() throws Exception {
        String t = customer();
        mvc.perform(auth(post("/api/cart/items"), t).content(item(pid("AP20W"), 1))).andExpect(status().isCreated());
        String s = guestAdd(null, pid("AP20W"), 2);
        guestAdd(s, pid("GWSTRAP"), 1);

        mvc.perform(auth(post("/api/cart/merge"), t).content("{\"sessionId\":\"" + s + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[?(@.productName=='Apple 20W USB-C Charger')].quantity", contains(3)))
                .andExpect(jsonPath("$.items[?(@.productName=='Galaxy Watch Strap')].quantity", contains(1)))
                .andExpect(jsonPath("$.totalItems").value(4));
        // the guest cart is gone
        mvc.perform(guest(get("/api/cart"), s)).andExpect(jsonPath("$.items", hasSize(0)));
        assertThat(jdbc.queryForObject("select count(*) from carts where session_id = ?", Integer.class, s)).isZero();
    }

    @Test
    void mergeNeverExceedsStockAndNeverFails() throws Exception {
        String t = customer();
        Long strap = pid("GWSTRAP"); // stock 3
        mvc.perform(auth(post("/api/cart/items"), t).content(item(strap, 2))).andExpect(status().isCreated());
        String s = guestAdd(null, strap, 3);
        mvc.perform(auth(post("/api/cart/merge"), t).content("{\"sessionId\":\"" + s + "\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3)); // 2 + 3 clamped to the 3 in stock
    }

    @Test
    void mergeWithAnUnknownOrBadSessionLeavesTheCartAlone() throws Exception {
        String t = customer();
        mvc.perform(auth(post("/api/cart/items"), t).content(item(pid("AP20W"), 1))).andExpect(status().isCreated());
        mvc.perform(auth(post("/api/cart/merge"), t).content("{\"sessionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(auth(post("/api/cart/merge"), t).content("{\"sessionId\":\"nope\"}")).andExpect(status().isBadRequest());
        mvc.perform(auth(post("/api/cart/merge"), t).content("{}")).andExpect(status().isBadRequest());
    }
}
