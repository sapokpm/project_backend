package com.example.shop.dto.request;

import com.example.shop.validator.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @ValidPassword String newPassword) {
}
