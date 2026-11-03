package com.example.shop.repository;

import com.example.shop.model.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);

    Optional<Category> findBySlugAndIsActiveTrue(String slug);

    Optional<Category> findByIdAndIsActiveTrue(Long id);

    boolean existsBySlug(String slug);

    boolean existsByParentCategoryId(Long parentId);

    List<Category> findByParentCategoryIsNullAndIsActiveTrueOrderBySortOrderAscIdAsc();

    List<Category> findByParentCategoryIdAndIsActiveTrueOrderBySortOrderAscIdAsc(Long parentId);
}
