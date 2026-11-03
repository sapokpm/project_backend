package com.example.shop.config;

import com.example.shop.security.RestSecurityErrorHandler;
import com.example.shop.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // turns on @PreAuthorize
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final RestSecurityErrorHandler errorHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, RestSecurityErrorHandler errorHandler) {
        this.jwtFilter = jwtFilter;
        this.errorHandler = errorHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // stateless API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        // first match wins: specific rules before the general ones
                        .requestMatchers("/api/auth/change-password").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        // guests may keep a cart; folding it into an account needs a login
                        .requestMatchers("/api/cart/merge", "/api/cart/apply-promo", "/api/cart/promo").authenticated()
                        .requestMatchers("/api/cart/**", "/api/cart").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/delivery/calculate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**",
                                "/api/brands/**", "/api/delivery/methods", "/api/payments/methods").permitAll()
                        // staff areas are decided here, before any request body is read, so a wrong role is always 403
                        .requestMatchers("/api/admin/orders/**", "/api/admin/orders").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
