package com.example.shop.repository;

import com.example.shop.model.entity.ProductSpecification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, Long> {
    List<ProductSpecification> findByProductIdOrderBySortOrder(Long productId);
}
