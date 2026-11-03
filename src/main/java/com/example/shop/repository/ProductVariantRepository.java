package com.example.shop.repository;

import com.example.shop.model.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByProductIdAndIsActiveTrue(Long productId);

    Optional<ProductVariant> findByIdAndProductId(Long id, Long productId);

    boolean existsBySku(String sku);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.reservedQuantity = v.reservedQuantity + :qty "
            + "where v.id = :id and v.stockQuantity - v.reservedQuantity >= :qty")
    int reserve(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.reservedQuantity = v.reservedQuantity - :qty "
            + "where v.id = :id and v.reservedQuantity >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity - :qty, v.reservedQuantity = v.reservedQuantity - :qty "
            + "where v.id = :id and v.reservedQuantity >= :qty and v.stockQuantity >= :qty")
    int commit(@Param("id") Long id, @Param("qty") int qty);
}
