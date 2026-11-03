package com.example.shop.repository;

import com.example.shop.model.entity.Review;
import com.example.shop.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    // the author is shown on every row, so load it in the same query
    @EntityGraph(attributePaths = {"user"})
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    Optional<Review> findByIdAndProductId(Long id, Long productId);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Query("select avg(r.rating) from Review r where r.product.id = :productId and r.status = com.example.shop.model.enums.ReviewStatus.APPROVED")
    Double averageApprovedRating(@Param("productId") Long productId);

    long countByProductIdAndStatus(Long productId, ReviewStatus status);

    /** Rows of [rating, count] for approved reviews; ratings with no reviews are absent. */
    @Query("select r.rating, count(r) from Review r where r.product.id = :productId "
            + "and r.status = com.example.shop.model.enums.ReviewStatus.APPROVED group by r.rating")
    List<Object[]> ratingDistribution(@Param("productId") Long productId);
}
