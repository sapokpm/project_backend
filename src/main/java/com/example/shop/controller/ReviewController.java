package com.example.shop.controller;

import com.example.shop.dto.request.ReviewRequest;
import com.example.shop.dto.response.ReviewResponse;
import com.example.shop.dto.response.ReviewSummaryResponse;
import com.example.shop.security.service.UserPrincipal;
import com.example.shop.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ReviewController {
    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping
    public Page<ReviewResponse> list(@PathVariable Long productId, @PageableDefault(size = 10) Pageable pageable) {
        return reviews.list(productId, pageable);
    }

    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@PathVariable Long productId) {
        return reviews.summary(productId);
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> create(@PathVariable Long productId,
                                                 @AuthenticationPrincipal UserPrincipal me,
                                                 @Valid @RequestBody ReviewRequest request) {
        ReviewResponse created = reviews.create(me.getId(), productId, request);
        return ResponseEntity.created(URI.create("/api/products/" + productId + "/reviews/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ReviewResponse update(@PathVariable Long productId, @PathVariable Long id,
                                 @AuthenticationPrincipal UserPrincipal me,
                                 @Valid @RequestBody ReviewRequest request) {
        return reviews.update(me.getId(), productId, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal me) {
        reviews.delete(me.getId(), me.getRole(), productId, id);
        return ResponseEntity.noContent().build();
    }
}
