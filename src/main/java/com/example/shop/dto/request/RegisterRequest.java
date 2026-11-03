package com.example.shop.dto.request;

import com.example.shop.validator.UniqueEmail;
import com.example.shop.validator.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) @UniqueEmail String email,
        @NotBlank @ValidPassword String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 32) String phone) {
}
