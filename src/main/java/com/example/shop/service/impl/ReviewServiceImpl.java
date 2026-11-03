package com.example.shop.service.impl;

import com.example.shop.dto.request.ReviewRequest;
import com.example.shop.dto.response.ReviewResponse;
import com.example.shop.dto.response.ReviewSummaryResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.DuplicateResourceException;
import com.example.shop.exception.ForbiddenException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.ReviewMapper;
import com.example.shop.model.entity.Product;
import com.example.shop.model.entity.Review;
import com.example.shop.model.enums.ReviewStatus;
import com.example.shop.model.enums.UserRole;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.repository.ReviewRepository;
import com.example.shop.repository.UserRepository;
import com.example.shop.service.ProductService;
import com.example.shop.service.ReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class ReviewServiceImpl implements ReviewService {
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "rating", "rating",
            "helpful", "helpfulCount");

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;
    private final ProductService productService;
    private final ReviewMapper mapper;
    private final boolean autoApprove;

    public ReviewServiceImpl(ReviewRepository reviews, ProductRepository products, UserRepository users,
                             OrderRepository orders, ProductService productService, ReviewMapper mapper,
                             @Value("${shop.reviews.auto-approve:true}") boolean autoApprove) {
        this.reviews = reviews;
        this.products = products;
        this.users = users;
        this.orders = orders;
        this.productService = productService;
        this.mapper = mapper;
        this.autoApprove = autoApprove;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> list(Long productId, Pageable pageable) {
        requireActiveProduct(productId);
        return reviews.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, safe(pageable)).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long productId) {
        requireActiveProduct(productId);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) distribution.put(star, 0L); // every star present, even with 0 reviews
        long total = 0;
        long weighted = 0;
        for (Object[] row : reviews.ratingDistribution(productId)) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            distribution.put(star, count);
            total += count;
            weighted += (long) star * count;
        }
        BigDecimal avg = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ReviewSummaryResponse(avg, total, distribution);
    }

    @Override
    @Transactional
    public ReviewResponse create(Long userId, Long productId, ReviewRequest request) {
        var product = requireActiveProduct(productId);
        if (reviews.existsByUserIdAndProductId(userId, productId)) {
            throw new DuplicateResourceException("You have already reviewed this product");
        }
        Review r = new Review();
        mapper.apply(request, r);
        r.setProduct(product);
        r.setUser(users.getReferenceById(userId));
        // the only source of truth: has this customer received this product?
        r.setIsVerifiedPurchase(orders.hasDeliveredProduct(userId, productId));
        r.setStatus(autoApprove ? ReviewStatus.APPROVED : ReviewStatus.PENDING);
        reviews.save(r);
        productService.recalculateRating(productId);
        return mapper.toResponse(r);
    }

    @Override
    @Transactional
    public ReviewResponse update(Long userId, Long productId, Long reviewId, ReviewRequest request) {
        requireActiveProduct(productId);
        Review r = review(productId, reviewId);
        if (!r.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only edit your own review");
        }
        mapper.apply(request, r);
        // an edited review goes back through moderation when moderation is on
        r.setStatus(autoApprove ? ReviewStatus.APPROVED : ReviewStatus.PENDING);
        reviews.flush();
        productService.recalculateRating(productId);
        return mapper.toResponse(r);
    }

    @Override
    @Transactional
    public void delete(Long userId, UserRole role, Long productId, Long reviewId) {
        Review r = review(productId, reviewId);
        boolean staff = role == UserRole.ADMIN || role == UserRole.MANAGER;
        if (!staff && !r.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You can only delete your own review");
        }
        reviews.delete(r);
        reviews.flush();
        productService.recalculateRating(productId);
    }

    private Review review(Long productId, Long reviewId) {
        // looked up through the product, so /products/1/reviews/{id of a review on product 2} is a 404
        return reviews.findByIdAndProductId(reviewId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("No review " + reviewId + " on product " + productId));
    }

    private Product requireActiveProduct(Long productId) {
        return products.findByIdAndIsActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("No product with id " + productId));
    }

    private static Pageable safe(Pageable pageable) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) {
            String property = SORTABLE.get(o.getProperty());
            if (property == null) {
                throw new BadRequestException("Cannot sort by '" + o.getProperty() + "'. Allowed: "
                        + new TreeSet<>(SORTABLE.keySet()));
            }
            orders.add(new Sort.Order(o.getDirection(), property));
        }
        if (orders.isEmpty()) orders.add(Sort.Order.desc("createdAt"));
        orders.add(Sort.Order.desc("id"));
        return PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100), Sort.by(orders));
    }
}
