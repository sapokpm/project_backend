package com.example.shop.repository;

import com.example.shop.model.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlug(String slug);

    Optional<Product> findByIdAndIsActiveTrue(Long id);

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByBrandId(Long brandId);

    Page<Product> findByIsActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndIsActiveTrue(Long categoryId, Pageable pageable);

    Page<Product> findByBrandIdAndIsActiveTrue(Long brandId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name, Pageable pageable);

    // brand is loaded in the same query; images/specs are batch-fetched (default_batch_fetch_size)
    @Override
    @EntityGraph(attributePaths = {"brand"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Atomic: succeeds only if enough is still available, so two buyers cannot both take the last unit. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.reservedQuantity = p.reservedQuantity + :qty "
            + "where p.id = :id and p.stockQuantity - p.reservedQuantity >= :qty")
    int reserve(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.reservedQuantity = p.reservedQuantity - :qty "
            + "where p.id = :id and p.reservedQuantity >= :qty")
    int release(@Param("id") Long id, @Param("qty") int qty);

    /** Goods leave the warehouse: both on-hand and reserved drop, and sales are counted. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :qty, p.reservedQuantity = p.reservedQuantity - :qty, "
            + "p.salesCount = p.salesCount + :qty where p.id = :id and p.reservedQuantity >= :qty and p.stockQuantity >= :qty")
    int commit(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.salesCount = p.salesCount + :qty where p.id = :id")
    void addSales(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.viewCount = p.viewCount + 1 where p.id = :id")
    void incrementViews(@Param("id") Long id);
}
