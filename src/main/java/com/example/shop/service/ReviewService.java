package com.example.shop.service;

import com.example.shop.dto.request.ReviewRequest;
import com.example.shop.dto.response.ReviewResponse;
import com.example.shop.dto.response.ReviewSummaryResponse;
import com.example.shop.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    /** Approved reviews only. */
    Page<ReviewResponse> list(Long productId, Pageable pageable);

    ReviewSummaryResponse summary(Long productId);

    /** One review per customer per product. isVerifiedPurchase is decided here, never taken from the client. */
    ReviewResponse create(Long userId, Long productId, ReviewRequest request);

    /** Authors edit their own review only. */
    ReviewResponse update(Long userId, Long productId, Long reviewId, ReviewRequest request);

    /** Authors delete their own; ADMIN and MANAGER may delete any. */
    void delete(Long userId, UserRole role, Long productId, Long reviewId);
}
