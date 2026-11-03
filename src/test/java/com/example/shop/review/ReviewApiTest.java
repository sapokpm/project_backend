package com.example.shop.review;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seeded customer (Aida) already reviewed 10 products; XP1V (Xperia) and the others below are unreviewed. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private Long pid(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private String token(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String customer() throws Exception { return token("customer@shop.local", "Customer123!"); }

    private String admin() throws Exception { return token("admin@shop.local", "Admin123!"); }

    private String newUser() throws Exception {
        String email = "reviewer-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Bo\",\"lastName\":\"Smith\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private JsonNode create(Long productId, String token, String body) throws Exception {
        String res = mvc.perform(as(post("/api/products/" + productId + "/reviews"), token).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    private static final String FIVE = "{\"rating\":5,\"title\":\"Great\",\"comment\":\"Works well\"}";

    @Test
    void listIsPublicApprovedOnlyAndHidesPrivateData() throws Exception {
        Long iphone = pid("IPH15");
        mvc.perform(get("/api/products/" + iphone + "/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].authorName").value("Aida C."))
                .andExpect(jsonPath("$.content[0].isVerifiedPurchase").value(true))
                .andExpect(jsonPath("$.content[0].status").value("APPROVED"));
        String raw = mvc.perform(get("/api/products/" + iphone + "/reviews")).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("customer@shop.local").doesNotContain("password");
        // GS24U's seeded review is PENDING, SRSXB100's is REJECTED
        mvc.perform(get("/api/products/" + pid("GS24U") + "/reviews")).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products/" + pid("SRSXB100") + "/reviews")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void summaryHasEveryStarAndTheAverage() throws Exception {
        mvc.perform(get("/api/products/" + pid("IPH15") + "/reviews/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(1)).andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.distribution.5").value(1)).andExpect(jsonPath("$.distribution.1").value(0));
        mvc.perform(get("/api/products/" + pid("XP1V") + "/reviews/summary"))
                .andExpect(jsonPath("$.totalReviews").value(0)).andExpect(jsonPath("$.averageRating").value(0))
                .andExpect(jsonPath("$.distribution", aMapWithSize(5)));
    }

    @Test
    void anonymousCannotWrite() throws Exception {
        Long p = pid("XP1V");
        mvc.perform(post("/api/products/" + p + "/reviews").contentType(MediaType.APPLICATION_JSON).content(FIVE))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/products/" + pid("IPH15") + "/reviews/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerPostsAReviewAndTheProductRatingUpdates() throws Exception {
        Long p = pid("XP1V");
        JsonNode r = create(p, customer(), "{\"rating\":4,\"title\":\"Good\",\"comment\":\"Solid phone\"}");
        assertThat(r.get("rating").asInt()).isEqualTo(4);
        assertThat(r.get("authorName").asText()).isEqualTo("Aida C.");
        mvc.perform(get("/api/products/" + p)).andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
        // a second customer pulls the average to 3.5 (rounded to 2 places)
        create(p, newUser(), "{\"rating\":3}");
        mvc.perform(get("/api/products/" + p)).andExpect(jsonPath("$.averageRating").value(3.5))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }

    @Test
    void clientCannotSetVerifiedFlagOrStatus() throws Exception {
        JsonNode r = create(pid("XP1V"), customer(),
                "{\"rating\":5,\"isVerifiedPurchase\":true,\"status\":\"REJECTED\",\"helpfulCount\":99,\"adminReply\":\"hi\"}");
        assertThat(r.get("isVerifiedPurchase").asBoolean()).isFalse(); // Aida never received an Xperia
        assertThat(r.get("status").asText()).isEqualTo("APPROVED");
        assertThat(r.get("helpfulCount").asInt()).isZero();
        assertThat(r.get("adminReply").isNull()).isTrue();
    }

    @Test
    void verifiedPurchaseComesFromADeliveredOrder() throws Exception {
        // Aida's DELIVERED order contains the iPhone 15; drop her seeded review so she can post again
        Long iphone = pid("IPH15");
        jdbc.update("delete from reviews where product_id = ?", iphone);
        assertThat(create(iphone, customer(), FIVE).get("isVerifiedPurchase").asBoolean()).isTrue();
        // ORD-2026-0002 (PAID, not delivered) has the MacBook Air: not verified
        Long macbook = pid("MBA13");
        jdbc.update("delete from reviews where product_id = ?", macbook);
        assertThat(create(macbook, customer(), FIVE).get("isVerifiedPurchase").asBoolean()).isFalse();
        // someone who bought nothing is never verified
        assertThat(create(pid("XP1V"), newUser(), FIVE).get("isVerifiedPurchase").asBoolean()).isFalse();
    }

    @Test
    void oneReviewPerCustomerPerProduct() throws Exception {
        String t = customer();
        Long p = pid("XP1V");
        create(p, t, FIVE);
        mvc.perform(as(post("/api/products/" + p + "/reviews"), t).content(FIVE)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You have already reviewed this product"));
        // Aida's seeded iPhone review counts too
        mvc.perform(as(post("/api/products/" + pid("IPH15") + "/reviews"), t).content(FIVE)).andExpect(status().isConflict());
    }

    @Test
    void invalidReviewsNameTheField() throws Exception {
        String t = customer();
        Long p = pid("XP1V");
        for (String bad : new String[]{"{\"rating\":6}", "{\"rating\":0}", "{\"title\":\"no rating\"}"}) {
            mvc.perform(as(post("/api/products/" + p + "/reviews"), t).content(bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.rating").exists());
        }
        mvc.perform(as(post("/api/products/" + p + "/reviews"), t).content("{broken")).andExpect(status().isBadRequest());
    }

    @Test
    void authorEditsTheirReviewAndTheRatingFollows() throws Exception {
        String t = customer();
        Long p = pid("XP1V");
        long id = create(p, t, "{\"rating\":5}").get("id").asLong();
        mvc.perform(as(put("/api/products/" + p + "/reviews/" + id), t).content("{\"rating\":2,\"comment\":\"changed my mind\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.comment").value("changed my mind"));
        mvc.perform(get("/api/products/" + p)).andExpect(jsonPath("$.averageRating").value(2.0));
    }

    @Test
    void nobodyEditsOrDeletesSomeoneElsesReview() throws Exception {
        Long p = pid("XP1V");
        long id = create(p, customer(), FIVE).get("id").asLong();
        String stranger = newUser();
        mvc.perform(as(put("/api/products/" + p + "/reviews/" + id), stranger).content("{\"rating\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/products/" + p + "/reviews/" + id), stranger)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/" + p + "/reviews/summary")).andExpect(jsonPath("$.totalReviews").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0)); // untouched
    }

    @Test
    void authorAndAdminCanDeleteAndTheRatingIsRecomputed() throws Exception {
        Long p = pid("XP1V");
        String t = customer();
        long mine = create(p, t, "{\"rating\":4}").get("id").asLong();
        mvc.perform(as(delete("/api/products/" + p + "/reviews/" + mine), t)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + p)).andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").value(0));
        long other = create(p, newUser(), "{\"rating\":1}").get("id").asLong();
        mvc.perform(as(delete("/api/products/" + p + "/reviews/" + other), admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + p + "/reviews/summary")).andExpect(jsonPath("$.totalReviews").value(0));
    }

    @Test
    void reviewIdMustBelongToTheProductInTheUrl() throws Exception {
        long id = create(pid("XP1V"), customer(), FIVE).get("id").asLong();
        Long other = pid("GS24U");
        mvc.perform(as(put("/api/products/" + other + "/reviews/" + id), customer()).content(FIVE)).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/products/" + other + "/reviews/" + id), admin())).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/products/" + pid("XP1V") + "/reviews/999999"), customer())).andExpect(status().isNotFound());
    }

    @Test
    void inactiveOrUnknownProductsHaveNoReviews() throws Exception {
        mvc.perform(get("/api/products/999999/reviews")).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/products/999999/reviews"), customer()).content(FIVE)).andExpect(status().isNotFound());
        Long p = pid("XP1V");
        jdbc.update("update products set is_active = false where id = ?", p);
        mvc.perform(get("/api/products/" + p + "/reviews")).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/products/" + p + "/reviews"), customer()).content(FIVE)).andExpect(status().isNotFound());
    }

    @Test
    void listIsPagedAndSortIsWhitelisted() throws Exception {
        Long p = pid("XP1V");
        create(p, customer(), "{\"rating\":2}");
        create(p, newUser(), "{\"rating\":5}");
        create(p, newUser(), "{\"rating\":3}");
        mvc.perform(get("/api/products/" + p + "/reviews?sort=rating,desc&size=2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].rating").value(5)).andExpect(jsonPath("$.content[1].rating").value(3));
        mvc.perform(get("/api/products/" + p + "/reviews?sort=user.email")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/" + p + "/reviews?sort=status")).andExpect(status().isBadRequest());
    }
}
