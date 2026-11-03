package com.example.shop.cart;

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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private String token() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"customer@shop.local\",\"password\":\"Customer123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void wishlistNeedsLogin() throws Exception {
        mvc.perform(get("/api/wishlist")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/wishlist/" + pid("IPH15"))).andExpect(status().isUnauthorized());
    }

    @Test
    void addListAndPriceDrop() throws Exception {
        String t = token();
        Long p = pid("AP20W"); // 19.00
        mvc.perform(as(post("/api/wishlist/" + p), t).content("{\"notifyOnPriceDrop\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.productName").value("Apple 20W USB-C Charger"))
                .andExpect(jsonPath("$.priceAtAdd").value(19.0)).andExpect(jsonPath("$.priceDropped").value(false));
        mvc.perform(as(get("/api/wishlist"), t)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].inStock").value(true));
        jdbc.update("update products set base_price = 15 where id = ?", p);
        em.clear(); // a real request would start with a fresh persistence context
        mvc.perform(as(get("/api/wishlist"), t)).andExpect(jsonPath("$.content[0].currentPrice").value(15.0))
                .andExpect(jsonPath("$.content[0].priceAtAdd").value(19.0)).andExpect(jsonPath("$.content[0].priceDropped").value(true));
    }

    @Test
    void bodyIsOptionalAndFlagsDefaultOff() throws Exception {
        mvc.perform(as(post("/api/wishlist/" + pid("MAGSAFE")), token())).andExpect(status().isCreated());
        Boolean notify = jdbc.queryForObject("select notify_on_price_drop from wishlists w join products p on p.id = w.product_id where p.sku = 'MAGSAFE'", Boolean.class);
        org.assertj.core.api.Assertions.assertThat(notify).isFalse();
    }

    @Test
    void duplicatesUnknownAndInactiveProductsAreRefused() throws Exception {
        String t = token();
        Long p = pid("IPH15");
        mvc.perform(as(post("/api/wishlist/" + p), t)).andExpect(status().isCreated());
        mvc.perform(as(post("/api/wishlist/" + p), t)).andExpect(status().isConflict());
        mvc.perform(as(post("/api/wishlist/999999"), t)).andExpect(status().isNotFound());
        jdbc.update("update products set is_active = false where sku = 'GS24'");
        mvc.perform(as(post("/api/wishlist/" + pid("GS24")), t)).andExpect(status().isNotFound());
    }

    @Test
    void removeThenRemoveAgain() throws Exception {
        String t = token();
        Long p = pid("IPH15");
        mvc.perform(as(post("/api/wishlist/" + p), t)).andExpect(status().isCreated());
        mvc.perform(as(delete("/api/wishlist/" + p), t)).andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/wishlist/" + p), t)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/wishlist"), t)).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void moveToCartAddsOneUnitAndRemovesTheEntry() throws Exception {
        String t = token();
        Long p = pid("AP20W");
        mvc.perform(as(post("/api/wishlist/" + p), t)).andExpect(status().isCreated());
        mvc.perform(as(post("/api/wishlist/" + p + "/move-to-cart"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.items[0].quantity").value(1));
        mvc.perform(as(get("/api/wishlist"), t)).andExpect(jsonPath("$.totalElements").value(0));
        // moving a second time: no longer on the wishlist
        mvc.perform(as(post("/api/wishlist/" + p + "/move-to-cart"), t)).andExpect(status().isNotFound());
    }

    @Test
    void moveToCartKeepsTheEntryWhenOutOfStock() throws Exception {
        String t = token();
        Long p = pid("GWSTRAP");
        mvc.perform(as(post("/api/wishlist/" + p), t)).andExpect(status().isCreated());
        jdbc.update("update products set reserved_quantity = stock_quantity where id = ?", p);
        em.clear();
        mvc.perform(as(post("/api/wishlist/" + p + "/move-to-cart"), t)).andExpect(status().isConflict());
        mvc.perform(as(get("/api/wishlist"), t)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].inStock").value(false));
        mvc.perform(as(get("/api/cart"), t)).andExpect(jsonPath("$.items", hasSize(0)));
    }
}
