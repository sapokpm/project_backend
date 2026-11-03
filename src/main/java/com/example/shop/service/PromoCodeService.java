package com.example.shop.service;

import com.example.shop.dto.request.PromoCodeRequest;
import com.example.shop.dto.request.ValidatePromoRequest;
import com.example.shop.dto.response.PromoCodeResponse;
import com.example.shop.dto.response.PromoValidationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PromoCodeService {
    /**
     * Checks a code against the lines and returns the discount, or throws PromoCodeInvalidException saying why:
     * unknown/inactive, not yet valid, expired, used up, used by this customer too often, below the minimum
     * order amount, or not applicable to anything in the cart.
     */
    PromoResult evaluate(String code, Long userId, List<PromoLine> lines);

    /** The customer-facing check: against the caller's cart, or against a plain orderAmount when one is given. */
    PromoValidationResponse validate(Long userId, ValidatePromoRequest request);

    /** Called by order creation, in the same transaction: counts the use and records who used it on which order. */
    void recordUsage(Long promoId, Long userId, Long orderId, java.math.BigDecimal discount);

    /** Called when an order is cancelled: gives the use back. */
    void releaseUsage(Long orderId);

    // ---- staff (admin controller later) ----
    Page<PromoCodeResponse> list(Pageable pageable);

    PromoCodeResponse create(Long adminId, PromoCodeRequest request);

    PromoCodeResponse update(Long id, PromoCodeRequest request);

    /** Unused codes are deleted; codes with history are only deactivated, so past orders keep their record. */
    void delete(Long id);
}
