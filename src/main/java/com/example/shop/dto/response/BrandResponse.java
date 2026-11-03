package com.example.shop.dto.response;

public record BrandResponse(Long id, String name, String slug, String description, String logoUrl,
                            String websiteUrl, String country, boolean isActive) {
}
