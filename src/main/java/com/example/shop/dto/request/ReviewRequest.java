package com.example.shop.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// no status, isVerifiedPurchase or counters: the service owns those
public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 200) String title,
        @Size(max = 5000) String comment,
        @Size(max = 2000) String pros,
        @Size(max = 2000) String cons) {
}
