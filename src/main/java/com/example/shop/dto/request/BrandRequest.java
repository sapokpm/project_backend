package com.example.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @Size(max = 500) String logoUrl,
        @Size(max = 500) String websiteUrl,
        @Size(max = 100) String country) {
}
