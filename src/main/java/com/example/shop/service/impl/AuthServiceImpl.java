package com.example.shop.service.impl;

import com.example.shop.dto.request.ChangePasswordRequest;
import com.example.shop.dto.request.LoginRequest;
import com.example.shop.dto.request.RefreshTokenRequest;
import com.example.shop.dto.request.RegisterRequest;
import com.example.shop.dto.response.AuthResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.exception.UnauthorizedException;
import com.example.shop.mapper.UserMapper;
import com.example.shop.model.entity.RefreshToken;
import com.example.shop.model.entity.User;
import com.example.shop.model.enums.UserRole;
import com.example.shop.repository.RefreshTokenRepository;
import com.example.shop.repository.UserRepository;
import com.example.shop.security.jwt.JwtTokenProvider;
import com.example.shop.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String BAD_LOGIN = "Invalid email or password";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtTokenProvider jwt;
    private final UserMapper mapper;

    public AuthServiceImpl(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                           AuthenticationManager authManager, JwtTokenProvider jwt, UserMapper mapper) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.authManager = authManager;
        this.jwt = jwt;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest r) {
        User u = new User();
        u.setEmail(r.email().trim().toLowerCase());
        u.setPassword(encoder.encode(r.password())); // BCrypt hash only
        u.setFirstName(r.firstName().trim());
        u.setLastName(r.lastName().trim());
        u.setPhone(r.phone());
        u.setRole(UserRole.CUSTOMER); // never taken from the request
        u.setLastLoginAt(LocalDateTime.now());
        users.save(u);
        return issueTokens(u);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase();
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, r.password()));
        } catch (AuthenticationException e) {
            // same message for unknown email, wrong password and disabled account
            throw new UnauthorizedException(BAD_LOGIN);
        }
        User u = users.findByEmail(email).orElseThrow(() -> new UnauthorizedException(BAD_LOGIN));
        u.setLastLoginAt(LocalDateTime.now());
        return issueTokens(u);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest r) {
        RefreshToken stored = refreshTokens.findByToken(JwtTokenProvider.hash(r.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.getRevoked() || stored.getExpiryDate().isBefore(LocalDateTime.now())
                || !Boolean.TRUE.equals(stored.getUser().getEnabled())) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        stored.setRevoked(true); // rotation: each refresh token works once
        return issueTokens(stored.getUser());
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest r) {
        refreshTokens.findByToken(JwtTokenProvider.hash(r.refreshToken()))
                .ifPresent(t -> t.setRevoked(true)); // unknown token: nothing to do, same answer
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest r) {
        User u = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!encoder.matches(r.currentPassword(), u.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        u.setPassword(encoder.encode(r.newPassword()));
        refreshTokens.revokeAllForUser(userId); // other devices must log in again
    }

    private AuthResponse issueTokens(User u) {
        String raw = jwt.generateRefreshTokenValue();
        RefreshToken t = new RefreshToken();
        t.setToken(JwtTokenProvider.hash(raw));
        t.setExpiryDate(LocalDateTime.now().plus(jwt.refreshTtl()));
        t.setUser(u);
        refreshTokens.save(t);
        return new AuthResponse(jwt.generateAccessToken(u), raw, "Bearer", jwt.accessTtlSeconds(),
                mapper.toResponse(u));
    }
}
