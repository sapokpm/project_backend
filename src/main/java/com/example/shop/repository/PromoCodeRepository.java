package com.example.shop.repository;

import com.example.shop.model.entity.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /** Atomic: only succeeds while the global usage limit has room, so two orders cannot both take the last use. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PromoCode p set p.currentUsageCount = p.currentUsageCount + 1 "
            + "where p.id = :id and (p.maxUsageCount is null or p.currentUsageCount < p.maxUsageCount)")
    int consumeOne(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PromoCode p set p.currentUsageCount = p.currentUsageCount - 1 "
            + "where p.id = :id and p.currentUsageCount > 0")
    int releaseOne(@Param("id") Long id);
}
