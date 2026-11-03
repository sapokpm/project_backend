package com.example.shop.user;

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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private record Account(String email, String access, String refresh) {
    }

    private Account register() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Str0ngPass\",\"firstName\":\"Una\",\"lastName\":\"User\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode n = json.readTree(body);
        return new Account(email, n.get("accessToken").asText(), n.get("refreshToken").asText());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t).contentType(MediaType.APPLICATION_JSON);
    }

    private static String address(String city, Boolean isDefault) {
        return "{\"title\":\"T\",\"country\":\"Kazakhstan\",\"city\":\"" + city + "\",\"street\":\"Abay 1\","
                + "\"recipientName\":\"Una User\",\"recipientPhone\":\"+77010000000\""
                + (isDefault == null ? "" : ",\"isDefault\":" + isDefault) + "}";
    }

    private long addAddress(String t, String city, Boolean isDefault) throws Exception {
        String body = mvc.perform(as(post("/api/users/me/addresses"), t).content(address(city, isDefault)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    // ---------------------------------------------------------------- profile

    @Test
    void meShowsTheProfileWithoutAnySecret() throws Exception {
        Account a = register();
        String raw = mvc.perform(as(get("/api/users/me"), a.access())).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(a.email())).andExpect(jsonPath("$.firstName").value("Una"))
                .andExpect(jsonPath("$.role").value("CUSTOMER")).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("password").doesNotContain("$2");
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void profileUpdateChangesNameAndPhoneOnly() throws Exception {
        Account a = register();
        mvc.perform(as(put("/api/users/me"), a.access()).content(
                        "{\"firstName\":\"Una2\",\"lastName\":\"Z\",\"phone\":\"+7701\",\"email\":\"evil@x.com\",\"role\":\"ADMIN\",\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Una2"))
                .andExpect(jsonPath("$.phone").value("+7701")).andExpect(jsonPath("$.email").value(a.email()))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
        mvc.perform(as(put("/api/users/me"), a.access()).content("{\"firstName\":\"\",\"lastName\":\"Z\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.firstName").exists());
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordAndAStrongNewOne() throws Exception {
        Account a = register();
        mvc.perform(as(put("/api/users/me/password"), a.access()).content("{\"currentPassword\":\"wrong\",\"newPassword\":\"N3wStrongPw\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(put("/api/users/me/password"), a.access()).content("{\"currentPassword\":\"Str0ngPass\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.newPassword").exists());
        mvc.perform(as(put("/api/users/me/password"), a.access()).content("{\"currentPassword\":\"Str0ngPass\",\"newPassword\":\"N3wStrongPw\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email() + "\",\"password\":\"Str0ngPass\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email() + "\",\"password\":\"N3wStrongPw\"}")).andExpect(status().isOk());
    }

    // -------------------------------------------------------------- addresses

    @Test
    void theFirstAddressIsTheDefaultAndAnotherCanTakeOver() throws Exception {
        Account a = register();
        long first = addAddress(a.access(), "Almaty", null);
        long second = addAddress(a.access(), "Astana", null);
        mvc.perform(as(get("/api/users/me/addresses/" + first), a.access())).andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(as(get("/api/users/me/addresses/" + second), a.access())).andExpect(jsonPath("$.isDefault").value(false));

        long third = addAddress(a.access(), "Shymkent", true);
        mvc.perform(as(get("/api/users/me/addresses"), a.access())).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(third)) // default first
                .andExpect(jsonPath("$.content[*].isDefault", contains(true, false, false)));
    }

    @Test
    void creatingReturns201WithALocationHeader() throws Exception {
        Account a = register();
        mvc.perform(as(post("/api/users/me/addresses"), a.access()).content(address("Almaty", null)))
                .andExpect(status().isCreated()).andExpect(header().string("Location", matchesPattern("/api/users/me/addresses/\\d+")))
                .andExpect(jsonPath("$.city").value("Almaty"));
    }

    @Test
    void addressUpdateEditsFieldsAndKeepsExactlyOneDefault() throws Exception {
        Account a = register();
        long first = addAddress(a.access(), "Almaty", null);
        long second = addAddress(a.access(), "Astana", null);
        mvc.perform(as(put("/api/users/me/addresses/" + second), a.access()).content(address("Astana2", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("Astana2")).andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(as(get("/api/users/me/addresses/" + first), a.access())).andExpect(jsonPath("$.isDefault").value(false));
        // un-ticking the default does not leave the customer with none
        mvc.perform(as(put("/api/users/me/addresses/" + second), a.access()).content(address("Astana2", false)))
                .andExpect(jsonPath("$.isDefault").value(true));
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from addresses a join users u on u.id = a.user_id "
                + "where u.email = ? and a.is_default", Integer.class, a.email())).isEqualTo(1);
    }

    @Test
    void deletingTheDefaultPromotesTheNewestRemainingAddress() throws Exception {
        Account a = register();
        long first = addAddress(a.access(), "Almaty", null);   // default
        long second = addAddress(a.access(), "Astana", null);
        long third = addAddress(a.access(), "Shymkent", null);
        mvc.perform(as(delete("/api/users/me/addresses/" + first), a.access())).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mvc.perform(as(get("/api/users/me/addresses/" + third), a.access())).andExpect(jsonPath("$.isDefault").value(true));
        mvc.perform(as(get("/api/users/me/addresses/" + second), a.access())).andExpect(jsonPath("$.isDefault").value(false));
        mvc.perform(as(get("/api/users/me/addresses/" + first), a.access())).andExpect(status().isNotFound());
    }

    @Test
    void nobodyTouchesSomeoneElsesAddress() throws Exception {
        Account owner = register();
        Account other = register();
        long id = addAddress(owner.access(), "Almaty", null);
        mvc.perform(as(get("/api/users/me/addresses/" + id), other.access())).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/users/me/addresses/" + id), other.access()).content(address("Hacked", null))).andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/users/me/addresses/" + id), other.access())).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/users/me/addresses"), other.access())).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(as(get("/api/users/me/addresses/" + id), owner.access())).andExpect(jsonPath("$.city").value("Almaty"));
        mvc.perform(get("/api/users/me/addresses")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAddressesNameTheFailingFields() throws Exception {
        Account a = register();
        mvc.perform(as(post("/api/users/me/addresses"), a.access()).content("{\"city\":\"Almaty\",\"latitude\":123}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.country").exists()).andExpect(jsonPath("$.details.street").exists())
                .andExpect(jsonPath("$.details.recipientName").exists()).andExpect(jsonPath("$.details.latitude").exists());
    }

    @Test
    void thereIsALimitOnSavedAddresses() throws Exception {
        Account a = register();
        for (int i = 0; i < 20; i++) addAddress(a.access(), "City" + i, null);
        mvc.perform(as(post("/api/users/me/addresses"), a.access()).content(address("One too many", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("at most 20")));
    }

    // ---------------------------------------------- the whole journey, by API only

    @Test
    void aNewCustomerCanGoFromRegistrationToAPaidOrderThroughTheApi() throws Exception {
        Account a = register();
        long addressId = addAddress(a.access(), "Almaty", null);
        Long product = jdbc.queryForObject("select id from products where sku = 'IPH15'", Long.class);
        mvc.perform(as(post("/api/cart/items"), a.access()).content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        String order = mvc.perform(as(post("/api/orders"), a.access()).content("{\"addressId\":" + addressId + ",\"deliveryMethod\":\"COURIER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(order).get("id").asLong();
        mvc.perform(as(post("/api/payments/process"), a.access()).content("{\"orderId\":" + orderId
                        + ",\"paymentMethod\":\"CASH_ON_DELIVERY\"}")).andExpect(status().isOk());
        mvc.perform(as(get("/api/orders/" + orderId), a.access())).andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.shippingAddress.city").value("Almaty"));
    }

    // ------------------------------------------------------------- close account

    @Test
    void deletingTheAccountDisablesItAndKillsEveryToken() throws Exception {
        Account a = register();
        mvc.perform(as(delete("/api/users/me"), a.access())).andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(as(get("/api/users/me"), a.access())).andExpect(status().isUnauthorized());   // the access token is dead at once
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + a.refresh() + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + a.email() + "\",\"password\":\"Str0ngPass\"}")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from users where email = ?", Integer.class, a.email())).isEqualTo(1); // row kept
    }

    @Test
    void anAccountWithAnOrderInProgressCannotBeClosed() throws Exception {
        Account a = register();
        long addressId = addAddress(a.access(), "Almaty", null);
        Long product = jdbc.queryForObject("select id from products where sku = 'AP20W'", Long.class);
        mvc.perform(as(post("/api/cart/items"), a.access()).content("{\"productId\":" + product + ",\"quantity\":1}")).andExpect(status().isCreated());
        mvc.perform(as(post("/api/orders"), a.access()).content("{\"addressId\":" + addressId + ",\"deliveryMethod\":\"COURIER\"}")).andExpect(status().isCreated());
        mvc.perform(as(delete("/api/users/me"), a.access())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("in progress")));
        mvc.perform(as(get("/api/users/me"), a.access())).andExpect(status().isOk()); // still works
    }
}
