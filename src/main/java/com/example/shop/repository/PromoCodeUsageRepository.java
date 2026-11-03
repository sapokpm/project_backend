package com.example.shop.repository;

import com.example.shop.model.entity.PromoCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, Long> {
    long countByPromoCodeIdAndUserId(Long promoCodeId, Long userId);

    boolean existsByPromoCodeId(Long promoCodeId);

    List<PromoCodeUsage> findByOrderId(Long orderId);
}
