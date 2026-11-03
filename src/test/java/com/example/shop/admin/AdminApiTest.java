package com.example.shop.admin;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Every admin area over HTTP: who may call it, the status codes, and the staff rules. Rolled back after each test. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private String login(String email, String pw) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + pw + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private String admin() throws Exception { return login("admin@shop.local", "Admin123!"); }

    private String customer() throws Exception { return login("customer@shop.local", "Customer123!"); }

    /** A registered customer promoted to MANAGER: may process orders but not manage the catalogue. */
    private String manager() throws Exception {
        String email = "mgr-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Mo\",\"lastName\":\"Mgr\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        jdbc.update("update users set role = 'MANAGER' where email = ?", email);
        em.clear();
        return json.readTree(body).get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON);
    }

    private Long id(String table, String column, String value) {
        return jdbc.queryForObject("select id from " + table + " where " + column + " = ?", Long.class, value);
    }

    private JsonNode body(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String productJson(String name, String sku, String price) {
        return "{\"name\":\"" + name + "\",\"sku\":\"" + sku + "\",\"basePrice\":" + price + ",\"stockQuantity\":10,\"categoryId\":"
                + id("categories", "slug", "audio") + "}";
    }

    // ---------------------------------------------------------------- who may call

    @Test
    void anonymousGets401CustomerGets403OnEveryAdminArea() throws Exception {
        String c = customer();
        String[][] calls = {
                {"GET", "/api/admin/orders"}, {"GET", "/api/admin/promo-codes"},
                {"POST", "/api/admin/products"}, {"POST", "/api/admin/categories"}, {"POST", "/api/admin/brands"},
                {"DELETE", "/api/admin/products/1"}, {"PUT", "/api/admin/orders/1/status"}};
        for (String[] call : calls) {
            MockHttpServletRequestBuilder anon = request(org.springframework.http.HttpMethod.valueOf(call[0]), call[1])
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
            mvc.perform(anon).andExpect(status().isUnauthorized());
            mvc.perform(as(request(org.springframework.http.HttpMethod.valueOf(call[0]), call[1]), c).content("{}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    @Test
    void managersProcessOrdersButCannotManageTheCatalogueOrPromos() throws Exception {
        String m = manager();
        mvc.perform(as(get("/api/admin/orders"), m)).andExpect(status().isOk());
        mvc.perform(as(post("/api/admin/products"), m).content(productJson("X", "X1", "10"))).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/categories"), m).content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/admin/brands"), m).content("{\"name\":\"X\"}")).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/promo-codes"), m)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/admin/products/" + id("products", "sku", "IPH15")), m)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- products

    @Test
    void createProductReturns201WithLocationAndTheNewProductIsPublic() throws Exception {
        String a = admin();
        JsonNode created = body(mvc.perform(as(post("/api/admin/products"), a).content(productJson("Pixel Buds", "PIXBUDS", "129.50")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/products/\\d+")))
                .andExpect(jsonPath("$.slug").value("pixel-buds")).andExpect(jsonPath("$.currentPrice").value(129.5))
                .andExpect(jsonPath("$.categoryName").value("Audio")));
        mvc.perform(get("/api/products/" + created.get("id").asLong())).andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("PIXBUDS"));
    }

    @Test
    void productValidationNamesFieldsAndDuplicatesAndMissingParentsAreRefused() throws Exception {
        String a = admin();
        mvc.perform(as(post("/api/admin/products"), a).content("{\"name\":\"A\",\"basePrice\":-5}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists()).andExpect(jsonPath("$.details.basePrice").exists())
                .andExpect(jsonPath("$.details.sku").exists()).andExpect(jsonPath("$.details.categoryId").exists());
        mvc.perform(as(post("/api/admin/products"), a).content(productJson("Dup", "IPH15", "10"))).andExpect(status().isConflict());
        mvc.perform(as(post("/api/admin/products"), a).content(productJson("Ghost", "GHOST1", "10").replaceAll("\"categoryId\":\\d+", "\"categoryId\":999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateChangesOnlyTheGivenFieldsAndChecksTheDiscount() throws Exception {
        String a = admin();
        Long iphone = id("products", "sku", "IPH15");
        mvc.perform(as(put("/api/admin/products/" + iphone), a).content("{\"name\":\"iPhone 15 Plus\",\"basePrice\":849}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("iPhone 15 Plus"))
                .andExpect(jsonPath("$.basePrice").value(849.0)).andExpect(jsonPath("$.sku").value("IPH15"));
        mvc.perform(as(put("/api/admin/products/" + iphone), a).content("{\"discountPrice\":900}")).andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/products/999999"), a).content("{\"name\":\"Nope\"}")).andExpect(status().isNotFound());
    }

    @Test
    void stockActivateDeactivateAndSoftDelete() throws Exception {
        String a = admin();
        Long strap = id("products", "sku", "GWSTRAP");
        mvc.perform(as(patch("/api/admin/products/" + strap + "/stock"), a).content("{\"stockQuantity\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableQuantity").value(50));
        mvc.perform(as(patch("/api/admin/products/" + strap + "/stock"), a).content("{\"stockQuantity\":-1}")).andExpect(status().isBadRequest());

        mvc.perform(as(patch("/api/admin/products/" + strap + "/deactivate"), a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        mvc.perform(get("/api/products/" + strap)).andExpect(status().isNotFound());
        mvc.perform(as(patch("/api/admin/products/" + strap + "/activate"), a)).andExpect(status().isOk());
        mvc.perform(get("/api/products/" + strap)).andExpect(status().isOk());

        mvc.perform(as(delete("/api/admin/products/" + strap), a)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/products/" + strap)).andExpect(status().isNotFound());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from products where id = ?", Integer.class, strap)).isEqualTo(1); // row kept
        mvc.perform(as(delete("/api/admin/products/999999"), a)).andExpect(status().isNotFound());
    }

    @Test
    void variantsCanBeAddedEditedAndRemoved() throws Exception {
        String a = admin();
        Long phone = id("products", "sku", "IPH15");
        String v = "{\"sku\":\"IPH15-512\",\"name\":\"512GB\",\"price\":1099,\"stockQuantity\":4}";
        long vid = body(mvc.perform(as(post("/api/admin/products/" + phone + "/variants"), a).content(v))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.availableQuantity").value(4))).get("id").asLong();
        mvc.perform(get("/api/products/" + phone + "/variants")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(as(post("/api/admin/products/" + phone + "/variants"), a).content(v)).andExpect(status().isConflict());
        mvc.perform(as(put("/api/admin/products/" + phone + "/variants/" + vid), a)
                        .content("{\"sku\":\"IPH15-512\",\"name\":\"512GB Pro\",\"price\":1149,\"stockQuantity\":6}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("512GB Pro")).andExpect(jsonPath("$.price").value(1149.0));
        mvc.perform(as(post("/api/admin/products/" + phone + "/variants"), a).content("{\"sku\":\"\",\"price\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.sku").exists());
        mvc.perform(as(delete("/api/admin/products/" + phone + "/variants/" + vid), a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + phone + "/variants")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(as(delete("/api/admin/products/" + phone + "/variants/999999"), a)).andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------- categories

    @Test
    void categoriesCreateMoveReorderAndRefuseUnsafeDeletes() throws Exception {
        String a = admin();
        JsonNode tv = body(mvc.perform(as(post("/api/admin/categories"), a).content("{\"name\":\"Smart TVs\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", matchesPattern("/api/categories/\\d+")))
                .andExpect(jsonPath("$.slug").value("smart-tvs")));
        long tvId = tv.get("id").asLong();
        long oled = body(mvc.perform(as(post("/api/admin/categories"), a).content("{\"name\":\"OLED\",\"parentCategoryId\":" + tvId + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parentId").value(tvId))).get("id").asLong();
        mvc.perform(as(put("/api/admin/categories/" + tvId), a).content("{\"name\":\"TVs\",\"parentCategoryId\":" + oled + "}"))
                .andExpect(status().isBadRequest()); // a category under its own child
        mvc.perform(as(put("/api/admin/categories/" + oled), a).content("{\"name\":\"OLED TVs\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("OLED TVs")).andExpect(jsonPath("$.parentId").doesNotExist());

        Long accessories = id("categories", "slug", "accessories");
        mvc.perform(as(put("/api/admin/categories/reorder"), a).content("{\"items\":[{\"id\":" + accessories + ",\"sortOrder\":0}]}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].name").value("Accessories"));
        mvc.perform(as(put("/api/admin/categories/reorder"), a).content("{\"items\":[]}")).andExpect(status().isBadRequest());

        mvc.perform(as(delete("/api/admin/categories/" + id("categories", "slug", "audio")), a)).andExpect(status().isConflict()); // has products
        mvc.perform(as(delete("/api/admin/categories/" + tvId), a)).andExpect(status().isNoContent()); // child moved out above, now empty
        mvc.perform(as(delete("/api/admin/categories/" + oled), a)).andExpect(status().isNoContent());
        mvc.perform(as(delete("/api/admin/categories/" + oled), a)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/admin/categories"), a).content("{\"name\":\"\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
    }

    // ------------------------------------------------------------------ brands

    @Test
    void brandsCreateUpdateAndRefuseToDeleteBrandsInUse() throws Exception {
        String a = admin();
        long nokia = body(mvc.perform(as(post("/api/admin/brands"), a).content("{\"name\":\"Nokia\",\"country\":\"Finland\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.slug").value("nokia")))
                .get("id").asLong();
        mvc.perform(as(put("/api/admin/brands/" + nokia), a).content("{\"name\":\"Nokia Oyj\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nokia Oyj"));
        mvc.perform(get("/api/brands/" + nokia)).andExpect(status().isOk());
        mvc.perform(as(delete("/api/admin/brands/" + id("brands", "slug", "apple")), a)).andExpect(status().isConflict());
        mvc.perform(as(delete("/api/admin/brands/" + nokia), a)).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/brands/" + nokia)).andExpect(status().isNotFound());
        mvc.perform(as(post("/api/admin/brands"), a).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
    }

    // ------------------------------------------------------------------- promos

    private static String promo(String code, String type, String value) {
        return "{\"code\":\"" + code + "\",\"discountType\":\"" + type + "\",\"discountValue\":" + value + "}";
    }

    @Test
    void promoCodesCreateListUpdateDelete() throws Exception {
        String a = admin();
        long id = body(mvc.perform(as(post("/api/admin/promo-codes"), a).content(promo("spring7", "PERCENTAGE", "7")))
                .andExpect(status().isCreated()).andExpect(header().string("Location", matchesPattern("/api/admin/promo-codes/\\d+")))
                .andExpect(jsonPath("$.code").value("SPRING7"))).get("id").asLong();
        mvc.perform(as(post("/api/admin/promo-codes"), a).content(promo("Spring7", "PERCENTAGE", "7"))).andExpect(status().isConflict());
        mvc.perform(as(post("/api/admin/promo-codes"), a).content(promo("BIG", "PERCENTAGE", "150"))).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/admin/promo-codes"), a).content("{\"code\":\"\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists()).andExpect(jsonPath("$.details.discountType").exists());

        mvc.perform(as(get("/api/admin/promo-codes?size=2"), a)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2))).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].code").value("SPRING7"));
        mvc.perform(as(put("/api/admin/promo-codes/" + id), a).content(promo("SPRING7", "PERCENTAGE", "9")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.discountValue").value(9.0));
        mvc.perform(as(put("/api/admin/promo-codes/999999"), a).content(promo("NOPE", "PERCENTAGE", "9"))).andExpect(status().isNotFound());

        mvc.perform(as(delete("/api/admin/promo-codes/" + id), a)).andExpect(status().isNoContent()).andExpect(content().string(""));
        // a code with order history is deactivated, not deleted
        Long used = id("promo_codes", "code", "WELCOME10");
        mvc.perform(as(delete("/api/admin/promo-codes/" + used), a)).andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select is_active from promo_codes where id = ?", Boolean.class, used)).isFalse();
    }

    // ------------------------------------------------------------------- orders

    @Test
    void ordersListFiltersSortsAndPages() throws Exception {
        String m = manager();
        mvc.perform(as(get("/api/admin/orders"), m)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].customerEmail").value("customer@shop.local"))
                .andExpect(jsonPath("$.content[0].order.orderNumber").value("ORD-2026-0005"));
        mvc.perform(as(get("/api/admin/orders?status=SHIPPED"), m)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].order.orderNumber").value("ORD-2026-0003"));
        mvc.perform(as(get("/api/admin/orders?search=0004"), m)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get("/api/admin/orders?size=2&page=2&sort=totalAmount,desc"), m)).andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(as(get("/api/admin/orders?from=2999-01-01"), m)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get("/api/admin/orders?status=NOPE"), m)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/orders?sort=user.password"), m)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/admin/orders?from=yesterday"), m)).andExpect(status().isBadRequest());
    }

    @Test
    void managersMoveOrdersThroughTheirStatusesAndIllegalMovesAreRefused() throws Exception {
        String m = manager();
        Long pending = id("orders", "order_number", "ORD-2026-0001");
        mvc.perform(as(put("/api/admin/orders/" + pending + "/status"), m).content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CONFLICT"));
        mvc.perform(as(put("/api/admin/orders/" + pending + "/status"), m).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CONFIRMED"));
        mvc.perform(as(put("/api/admin/orders/" + pending + "/status"), m).content("{\"status\":\"PENDING\"}")).andExpect(status().isConflict());
        mvc.perform(as(put("/api/admin/orders/" + id("orders", "order_number", "ORD-2026-0004") + "/status"), m)
                .content("{\"status\":\"REFUNDED\"}")).andExpect(status().isConflict()); // delivered: only through a return
        mvc.perform(as(put("/api/admin/orders/" + pending + "/status"), m).content("{\"status\":\"TELEPORTED\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/admin/orders/" + pending + "/status"), m).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
        mvc.perform(as(put("/api/admin/orders/999999/status"), m).content("{\"status\":\"PAID\"}")).andExpect(status().isNotFound());
    }

    @Test
    void trackingAndNotesAreStaffOnly() throws Exception {
        String a = admin();
        Long shipped = id("orders", "order_number", "ORD-2026-0003");
        mvc.perform(as(put("/api/admin/orders/" + shipped + "/tracking"), a).content("{\"trackingNumber\":\"KZ-998877\",\"estimatedDeliveryDate\":\"2030-01-15\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.deliveryTrackingNumber").value("KZ-998877"))
                .andExpect(jsonPath("$.order.estimatedDeliveryDate").value("2030-01-15"));
        mvc.perform(as(put("/api/admin/orders/" + shipped + "/tracking"), a).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.trackingNumber").exists());
        mvc.perform(as(put("/api/admin/orders/" + shipped + "/note"), a).content("{\"adminNote\":\"fragile\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.adminNote").value("fragile"));
        mvc.perform(as(get("/api/admin/orders/" + shipped), a)).andExpect(jsonPath("$.adminNote").value("fragile"));
        // the customer sees the tracking number but never the staff note
        String raw = mvc.perform(as(get("/api/orders/" + shipped), customer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryTrackingNumber").value("KZ-998877")).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("fragile").doesNotContain("adminNote");
        mvc.perform(as(put("/api/admin/orders/" + id("orders", "order_number", "ORD-2026-0005") + "/tracking"), a)
                .content("{\"trackingNumber\":\"X\"}")).andExpect(status().isConflict()); // cancelled
        mvc.perform(as(get("/api/admin/orders/999999"), a)).andExpect(status().isNotFound());
    }
}
