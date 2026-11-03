package com.example.shop.mapper;

import com.example.shop.dto.request.CategoryRequest;
import com.example.shop.dto.request.BrandRequest;
import com.example.shop.dto.response.BrandResponse;
import com.example.shop.dto.response.CategoryResponse;
import com.example.shop.model.entity.Brand;
import com.example.shop.model.entity.Category;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CategoryMapper {

    /** Recursive: includes the whole subtree, inactive branches left out. Call inside a transaction. */
    public CategoryResponse toTree(Category c) {
        List<CategoryResponse> kids = c.getChildren().stream()
                .filter(k -> Boolean.TRUE.equals(k.getIsActive()))
                .sorted(Comparator.comparing(Category::getSortOrder).thenComparing(Category::getId))
                .map(this::toTree).toList();
        return toResponse(c, kids);
    }

    /** No children loaded. */
    public CategoryResponse toResponse(Category c) {
        return toResponse(c, List.of());
    }

    private CategoryResponse toResponse(Category c, List<CategoryResponse> kids) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getImageUrl(),
                c.getSortOrder(), Boolean.TRUE.equals(c.getIsActive()),
                c.getParentCategory() == null ? null : c.getParentCategory().getId(), kids);
    }

    public void apply(CategoryRequest r, Category c) {
        c.setName(r.name());
        c.setDescription(r.description());
        c.setImageUrl(r.imageUrl());
        if (r.sortOrder() != null) c.setSortOrder(r.sortOrder());
    }

    public BrandResponse toResponse(Brand b) {
        return new BrandResponse(b.getId(), b.getName(), b.getSlug(), b.getDescription(), b.getLogoUrl(),
                b.getWebsiteUrl(), b.getCountry(), Boolean.TRUE.equals(b.getIsActive()));
    }

    public void apply(BrandRequest r, Brand b) {
        b.setName(r.name());
        b.setDescription(r.description());
        b.setLogoUrl(r.logoUrl());
        b.setWebsiteUrl(r.websiteUrl());
        b.setCountry(r.country());
    }
}
