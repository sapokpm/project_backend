package com.example.shop.repository;

import com.example.shop.model.entity.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findBySlug(String slug);

    Optional<Brand> findByIdAndIsActiveTrue(Long id);

    boolean existsBySlug(String slug);

    List<Brand> findByIsActiveTrue();

    Page<Brand> findByIsActiveTrue(Pageable pageable);
}
