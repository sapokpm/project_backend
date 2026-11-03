package com.example.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MergeCartRequest(@NotBlank @Size(max = 100) String sessionId) {
}
