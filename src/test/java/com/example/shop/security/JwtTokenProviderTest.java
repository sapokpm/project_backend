package com.example.shop.security;

import com.example.shop.model.entity.User;
import com.example.shop.model.enums.UserRole;
import com.example.shop.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));

    private User user() {
        User u = new User();
        u.setId(7L);
        u.setEmail("a@b.c");
        u.setRole(UserRole.CUSTOMER);
        return u;
    }

    @Test
    void validTokenRoundTrips() {
        String t = provider.generateAccessToken(user());
        assertThat(provider.isValid(t)).isTrue();
        assertThat(provider.getEmail(t)).isEqualTo("a@b.c");
    }

    @Test
    void tamperedTokensAreRejected() {
        String t = provider.generateAccessToken(user());
        String[] p = t.split("\\.");
        assertThat(provider.isValid(t + "x")).isFalse();
        assertThat(provider.isValid(p[0] + "." + p[1] + "A." + p[2])).isFalse();
        assertThat(provider.isValid(p[0] + "." + p[1] + ".")).isFalse();
        assertThat(provider.isValid("not.a.jwt")).isFalse();
        assertThat(provider.isValid("")).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        var other = new JwtTokenProvider("another-secret-another-secret-0123456789ab", Duration.ofMinutes(15), Duration.ofDays(7));
        assertThat(provider.isValid(other.generateAccessToken(user()))).isFalse();
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        var enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = enc.encodeToString("{\"sub\":\"admin@shop.local\"}".getBytes());
        assertThat(provider.isValid(header + "." + payload + ".")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        var expired = new JwtTokenProvider(SECRET, Duration.ofSeconds(-5), Duration.ofDays(7));
        assertThat(provider.isValid(expired.generateAccessToken(user()))).isFalse();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtTokenProvider("short", Duration.ofMinutes(1), Duration.ofDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refreshTokensAreRandomAndHashedConsistently() {
        String a = provider.generateRefreshTokenValue();
        String b = provider.generateRefreshTokenValue();
        assertThat(a).isNotEqualTo(b).hasSizeGreaterThan(40);
        assertThat(JwtTokenProvider.hash(a)).isEqualTo(JwtTokenProvider.hash(a)).hasSize(64).isNotEqualTo(a);
    }
}
