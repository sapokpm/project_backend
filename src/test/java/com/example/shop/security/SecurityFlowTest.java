package com.example.shop.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityFlowTest {
    private static final String PW = "Str0ngPass";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from users where email like 'sectest-%'");
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder postJson(String url, String content) {
        return post(url).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private JsonNode register(String email) throws Exception {
        MvcResult r = mvc.perform(postJson("/api/auth/register", """
                {"email":"%s","password":"%s","firstName":"Sec","lastName":"Test"}""".formatted(email, PW)))
                .andExpect(status().isCreated()).andReturn();
        return body(r);
    }

    private JsonNode login(String email, String pw) throws Exception {
        MvcResult r = mvc.perform(postJson("/api/auth/login", """
                {"email":"%s","password":"%s"}""".formatted(email, pw))).andExpect(status().isOk()).andReturn();
        return body(r);
    }

    private String newEmail() {
        return "sectest-" + UUID.randomUUID() + "@example.com";
    }

    @Test
    void registerReturnsTokensAndNeverThePassword() throws Exception {
        String email = newEmail();
        JsonNode res = register(email);
        assertThat(res.get("accessToken").asText()).isNotBlank();
        assertThat(res.get("refreshToken").asText()).isNotBlank();
        assertThat(res.get("user").get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(res.toString()).doesNotContain("password").doesNotContain("$2");
        String stored = jdbc.queryForObject("select password from users where email = ?", String.class, email);
        assertThat(stored).startsWith("$2"); // BCrypt hash, not plain text
    }

    @Test
    void registerCannotChooseAdminRole() throws Exception {
        String email = newEmail();
        mvc.perform(postJson("/api/auth/register", """
                {"email":"%s","password":"%s","firstName":"A","lastName":"B","role":"ADMIN"}""".formatted(email, PW)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void duplicateEmailAndWeakPasswordAreRefusedWithFieldNames() throws Exception {
        String email = newEmail();
        register(email);
        mvc.perform(postJson("/api/auth/register", """
                {"email":"%s","password":"%s","firstName":"A","lastName":"B"}""".formatted(email, PW)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists());
        mvc.perform(postJson("/api/auth/register", """
                {"email":"%s","password":"weak","firstName":"A","lastName":"B"}""".formatted(newEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void loginWorksForSeedUsersAndFailsGenerically() throws Exception {
        JsonNode res = login("customer@shop.local", "Customer123!");
        assertThat(res.get("accessToken").asText()).isNotBlank();
        String wrongPw = mvc.perform(postJson("/api/auth/login", "{\"email\":\"customer@shop.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String noUser = mvc.perform(postJson("/api/auth/login", "{\"email\":\"ghost@shop.local\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        // unknown email and wrong password are indistinguishable
        assertThat(json.readTree(wrongPw).get("message")).isEqualTo(json.readTree(noUser).get("message"));
    }

    @Test
    void anonymousIsRefusedWith401InTheStandardShape() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.details").isEmpty());
        mvc.perform(get("/api/admin/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedOrGarbageTokenIsTreatedAsAnonymous() throws Exception {
        String token = login("customer@shop.local", "Customer123!").get("accessToken").asText();
        mvc.perform(get("/api/orders").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerGets403OnAdminAndAdminDoesNot() throws Exception {
        String customer = login("customer@shop.local", "Customer123!").get("accessToken").asText();
        mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        String admin = login("admin@shop.local", "Admin123!").get("accessToken").asText();
        int s = mvc.perform(get("/api/admin/orders").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus();
        assertThat(s).isNotIn(401, 403); // no controller yet, so 404 is fine: security let it through
    }

    @Test
    void catalogueIsPublic() throws Exception {
        int s = mvc.perform(get("/api/products")).andReturn().getResponse().getStatus();
        assertThat(s).isNotIn(401, 403);
    }

    @Test
    void refreshRotatesAndOldTokenIsDead() throws Exception {
        JsonNode first = register(newEmail());
        String oldRefresh = first.get("refreshToken").asText();
        MvcResult r = mvc.perform(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String newRefresh = body(r).get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
        mvc.perform(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refresh = register(newEmail()).get("refreshToken").asText();
        mvc.perform(postJson("/api/auth/logout", "{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokensAreStoredHashedNotRaw() throws Exception {
        String email = newEmail();
        String raw = register(email).get("refreshToken").asText();
        Integer rawRows = jdbc.queryForObject("select count(*) from refresh_tokens where token = ?", Integer.class, raw);
        assertThat(rawRows).isZero();
    }

    @Test
    void changePasswordNeedsLoginChecksCurrentAndRevokesRefreshTokens() throws Exception {
        String email = newEmail();
        JsonNode reg = register(email);
        String access = reg.get("accessToken").asText();
        String refresh = reg.get("refreshToken").asText();
        String payload = "{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"N3wStrongPw\"}";

        mvc.perform(postJson("/api/auth/change-password", payload)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/change-password",
                        "{\"currentPassword\":\"wrong\",\"newPassword\":\"N3wStrongPw\"}")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/auth/change-password", payload).header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        mvc.perform(postJson("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isUnauthorized());
        login(email, "N3wStrongPw");
    }
}
