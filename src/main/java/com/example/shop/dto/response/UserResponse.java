package com.example.shop.dto.response;

import com.example.shop.model.enums.UserRole;

import java.time.LocalDateTime;

// no password, ever
public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserRole role,
        boolean emailVerified,
        LocalDateTime createdAt) {
}
