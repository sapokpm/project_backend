package com.example.shop.service.impl;

import com.example.shop.dto.request.CategoryRequest;
import com.example.shop.dto.request.ReorderCategoriesRequest;
import com.example.shop.dto.response.CategoryResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.ResourceInUseException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.CategoryMapper;
import com.example.shop.model.entity.Category;
import com.example.shop.repository.CategoryRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.service.CategoryService;
import com.example.shop.service.ProductService;
import com.example.shop.util.SlugUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductService productService;
    private final CategoryMapper mapper;

    public CategoryServiceImpl(CategoryRepository categories, ProductRepository products,
                               ProductService productService, CategoryMapper mapper) {
        this.categories = categories;
        this.products = products;
        this.productService = productService;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        return categories.findByParentCategoryIsNullAndIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(mapper::toTree).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return mapper.toTree(activeCategory(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findBySlug(String slug) {
        return mapper.toTree(categories.findBySlugAndIsActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No category with slug " + slug)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> products(Long id, Pageable pageable) {
        activeCategory(id); // 404 for unknown or inactive
        return productService.search(id, null, null, null, null, null, null, pageable);
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest r) {
        Category c = new Category();
        mapper.apply(r, c);
        c.setSlug(SlugUtil.uniqueSlug(r.name(), categories::existsBySlug));
        c.setParentCategory(r.parentCategoryId() == null ? null : category(r.parentCategoryId()));
        return mapper.toResponse(categories.save(c));
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest r) {
        Category c = category(id);
        Category newParent = r.parentCategoryId() == null ? null : category(r.parentCategoryId());
        if (newParent != null) {
            // a category cannot become its own ancestor
            for (Category up = newParent; up != null; up = up.getParentCategory()) {
                if (up.getId().equals(id)) {
                    throw new BadRequestException("A category cannot be moved under itself or one of its subcategories");
                }
            }
        }
        mapper.apply(r, c); // slug stays as it is: changing it would break existing links
        c.setParentCategory(newParent);
        return mapper.toResponse(c);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category c = category(id);
        if (categories.existsByParentCategoryId(id)) {
            throw new ResourceInUseException("Category has subcategories; move or delete them first");
        }
        if (products.existsByCategoryId(id)) {
            throw new ResourceInUseException("Category still has products; move them first");
        }
        categories.delete(c);
    }

    @Override
    @Transactional
    public void reorder(ReorderCategoriesRequest r) {
        for (ReorderCategoriesRequest.Item item : r.items()) {
            category(item.id()).setSortOrder(item.sortOrder()); // one unknown id rolls the whole change back
        }
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("No category with id " + id));
    }

    private Category activeCategory(Long id) {
        return categories.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("No category with id " + id));
    }
}
