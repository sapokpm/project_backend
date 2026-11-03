package com.example.shop.controller;

import com.example.shop.dto.response.CategoryResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.service.CategoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    // a tree has no page boundary: the response is the whole active tree
    @GetMapping
    public List<CategoryResponse> tree() {
        return categories.tree();
    }

    /** /{id} and /{slug} are the same URL shape, so one handler takes both: digits mean id, anything else a slug. */
    @GetMapping("/{idOrSlug}")
    public CategoryResponse one(@PathVariable String idOrSlug) {
        return idOrSlug.chars().allMatch(Character::isDigit) && idOrSlug.length() <= 18
                ? categories.findById(Long.parseLong(idOrSlug))
                : categories.findBySlug(idOrSlug);
    }

    @GetMapping("/{id}/products")
    public Page<ProductSummaryResponse> products(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return categories.products(id, pageable);
    }
}
