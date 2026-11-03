package com.example.shop.dto.response;

import java.util.List;

public record CategoryResponse(Long id, String name, String slug, String description, String imageUrl,
                               int sortOrder, boolean isActive, Long parentId, List<CategoryResponse> children) {
}
