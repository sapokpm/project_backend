package com.example.shop.dto.response;

import com.example.shop.model.enums.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long productId,
        String authorName,
        int rating,
        String title,
        String comment,
        String pros,
        String cons,
        boolean isVerifiedPurchase,
        int helpfulCount,
        int notHelpfulCount,
        String adminReply,
        ReviewStatus status,
        LocalDateTime createdAt) {
}
