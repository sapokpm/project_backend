package com.example.shop.service;

import com.example.shop.dto.request.CategoryRequest;
import com.example.shop.dto.request.ReorderCategoriesRequest;
import com.example.shop.dto.response.CategoryResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {
    /** Whole active tree: roots with nested children. A tree is bounded by the catalogue's shape, so not paged. */
    List<CategoryResponse> tree();

    CategoryResponse findById(Long id);

    CategoryResponse findBySlug(String slug);

    /** Products in the category and everything below it. */
    Page<ProductSummaryResponse> products(Long id, Pageable pageable);

    // ---- staff writes (admin controller later) ----
    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    /** Refused while the category has subcategories or products. */
    void delete(Long id);

    void reorder(ReorderCategoriesRequest request);
}
