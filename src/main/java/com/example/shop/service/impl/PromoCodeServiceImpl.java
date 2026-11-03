package com.example.shop.service.impl;

import com.example.shop.dto.request.PromoCodeRequest;
import com.example.shop.dto.request.ValidatePromoRequest;
import com.example.shop.dto.response.PromoCodeResponse;
import com.example.shop.dto.response.PromoValidationResponse;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.DuplicateResourceException;
import com.example.shop.exception.PromoCodeInvalidException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.PromoMapper;
import com.example.shop.model.entity.*;
import com.example.shop.model.enums.DiscountType;
import com.example.shop.repository.*;
import com.example.shop.service.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PromoCodeServiceImpl implements PromoCodeService {
    // one message for unknown and inactive codes: do not reveal which codes exist
    private static final String NOT_VALID = "Promo code is not valid";

    private final PromoCodeRepository promos;
    private final PromoCodeUsageRepository usages;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final CartRepository carts;
    private final UserRepository users;
    private final OrderRepository orders;
    private final CartPricing pricing;
    private final PromoMapper mapper;

    public PromoCodeServiceImpl(PromoCodeRepository promos, PromoCodeUsageRepository usages,
                                CategoryRepository categories, ProductRepository products, CartRepository carts,
                                UserRepository users, OrderRepository orders, CartPricing pricing, PromoMapper mapper) {
        this.promos = promos;
        this.usages = usages;
        this.categories = categories;
        this.products = products;
        this.carts = carts;
        this.users = users;
        this.orders = orders;
        this.pricing = pricing;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- customer

    @Override
    @Transactional(readOnly = true)
    public PromoResult evaluate(String code, Long userId, List<PromoLine> lines) {
        if (code == null || code.isBlank()) throw new PromoCodeInvalidException(NOT_VALID);
        PromoCode p = promos.findByCodeIgnoreCase(code.trim())
                .filter(x -> Boolean.TRUE.equals(x.getIsActive()))
                .orElseThrow(() -> new PromoCodeInvalidException(NOT_VALID));

        LocalDateTime now = LocalDateTime.now();
        if (p.getValidFrom() != null && now.isBefore(p.getValidFrom())) {
            throw new PromoCodeInvalidException("Promo code is not valid yet");
        }
        if (p.getValidTo() != null && now.isAfter(p.getValidTo())) {
            throw new PromoCodeInvalidException("Promo code has expired");
        }
        if (p.getMaxUsageCount() != null && p.getCurrentUsageCount() >= p.getMaxUsageCount()) {
            throw new PromoCodeInvalidException("Promo code has been fully used");
        }
        if (p.getMaxUsagePerUser() != null
                && usages.countByPromoCodeIdAndUserId(p.getId(), userId) >= p.getMaxUsagePerUser()) {
            throw new PromoCodeInvalidException("You have already used this promo code the maximum number of times");
        }

        BigDecimal cartTotal = pricing.subtotal(lines);
        if (p.getMinOrderAmount() != null && cartTotal.compareTo(p.getMinOrderAmount()) < 0) {
            throw new PromoCodeInvalidException("Orders must be at least " + p.getMinOrderAmount().toPlainString()
                    + " to use this code");
        }

        BigDecimal eligible = eligibleTotal(p, lines);
        if (eligible.signum() <= 0) {
            throw new PromoCodeInvalidException("Promo code does not apply to the items in your cart");
        }

        return switch (p.getDiscountType()) {
            case FREE_SHIPPING -> new PromoResult(p, BigDecimal.ZERO, true);
            case PERCENTAGE -> new PromoResult(p, capped(p, eligible.multiply(p.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP), eligible), false);
            case FIXED_AMOUNT -> new PromoResult(p, capped(p, p.getDiscountValue(), eligible), false);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public PromoValidationResponse validate(Long userId, ValidatePromoRequest r) {
        List<PromoLine> lines;
        if (r.orderAmount() != null) {
            PromoCode p = promos.findByCodeIgnoreCase(r.code().trim()).orElse(null);
            if (p != null && (!p.getApplicableCategories().isEmpty() || !p.getApplicableProducts().isEmpty())) {
                throw new PromoCodeInvalidException(
                        "This code applies only to selected products; add them to your cart and apply it there");
            }
            lines = List.of(new PromoLine(null, null, r.orderAmount()));
        } else {
            lines = carts.findByUserId(userId).map(pricing::lines).orElse(List.of());
            if (lines.isEmpty()) throw new BadRequestException("Provide orderAmount, or add items to your cart first");
        }
        PromoResult res = evaluate(r.code(), userId, lines);
        PromoCode p = res.promo();
        return new PromoValidationResponse(p.getCode(), p.getDiscountType(), p.getDiscountValue(), res.discount(),
                res.freeShipping() ? "Free shipping" : "Saves " + res.discount().toPlainString());
    }

    @Override
    @Transactional
    public void recordUsage(Long promoId, Long userId, Long orderId, BigDecimal discount) {
        if (promos.consumeOne(promoId) == 0) {
            throw new PromoCodeInvalidException("Promo code has been fully used");
        }
        PromoCodeUsage u = new PromoCodeUsage();
        u.setPromoCode(promos.getReferenceById(promoId));
        u.setUser(users.getReferenceById(userId));
        u.setOrder(orders.getReferenceById(orderId));
        u.setDiscountApplied(discount);
        usages.save(u);
    }

    @Override
    @Transactional
    public void releaseUsage(Long orderId) {
        List<PromoCodeUsage> used = usages.findByOrderId(orderId);
        for (PromoCodeUsage u : used) {
            promos.releaseOne(u.getPromoCode().getId());
        }
        usages.deleteAll(used);
    }

    // ---------------------------------------------------------------- staff

    @Override
    @Transactional(readOnly = true)
    public Page<PromoCodeResponse> list(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "id"));
        return promos.findAll(p).map(mapper::toResponse);
    }

    @Override
    @Transactional
    public PromoCodeResponse create(Long adminId, PromoCodeRequest r) {
        String code = r.code().trim().toUpperCase();
        if (promos.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Promo code " + code + " already exists");
        }
        checkRules(r);
        PromoCode p = new PromoCode();
        mapper.apply(r, p);
        p.setCreatedBy(users.getReferenceById(adminId));
        applyTargets(r, p);
        return mapper.toResponse(promos.save(p));
    }

    @Override
    @Transactional
    public PromoCodeResponse update(Long id, PromoCodeRequest r) {
        PromoCode p = promos.findById(id).orElseThrow(() -> new ResourceNotFoundException("No promo code with id " + id));
        String code = r.code().trim().toUpperCase();
        if (!p.getCode().equalsIgnoreCase(code) && promos.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Promo code " + code + " already exists");
        }
        checkRules(r);
        mapper.apply(r, p);
        applyTargets(r, p);
        return mapper.toResponse(p);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        PromoCode p = promos.findById(id).orElseThrow(() -> new ResourceNotFoundException("No promo code with id " + id));
        if (usages.existsByPromoCodeId(id)) {
            p.setIsActive(false);
        } else {
            promos.delete(p);
        }
    }

    // -------------------------------------------------------------- helpers

    /** Money-off is limited to the eligible items and to maxDiscountAmount. Never negative. */
    private static BigDecimal capped(PromoCode p, BigDecimal raw, BigDecimal eligible) {
        BigDecimal d = raw.min(eligible);
        if (p.getMaxDiscountAmount() != null) d = d.min(p.getMaxDiscountAmount());
        return d.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Lines the code applies to: everything when unrestricted, else products listed or inside a listed category tree. */
    private BigDecimal eligibleTotal(PromoCode p, List<PromoLine> lines) {
        Set<Long> productIds = new HashSet<>();
        p.getApplicableProducts().forEach(x -> productIds.add(x.getId()));
        Set<Long> categoryIds = new HashSet<>();
        p.getApplicableCategories().forEach(c -> categoryIds.addAll(withDescendants(c.getId())));
        boolean unrestricted = productIds.isEmpty() && categoryIds.isEmpty();

        BigDecimal total = BigDecimal.ZERO;
        for (PromoLine l : lines) {
            boolean applies = unrestricted
                    || (l.productId() != null && productIds.contains(l.productId()))
                    || (l.categoryId() != null && categoryIds.contains(l.categoryId()));
            if (applies) total = total.add(l.lineTotal());
        }
        return total;
    }

    private Set<Long> withDescendants(Long rootId) {
        Set<Long> seen = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>(List.of(rootId));
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!seen.add(id)) continue;
            categories.findByParentCategoryIdAndIsActiveTrueOrderBySortOrderAscIdAsc(id).forEach(c -> queue.add(c.getId()));
        }
        return seen;
    }

    private static void checkRules(PromoCodeRequest r) {
        DiscountType t = r.discountType();
        if (t == DiscountType.PERCENTAGE && (r.discountValue().signum() <= 0 || r.discountValue().compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new BadRequestException("A percentage discount must be above 0 and at most 100");
        }
        if (t == DiscountType.FIXED_AMOUNT && r.discountValue().signum() <= 0) {
            throw new BadRequestException("A fixed discount must be above 0");
        }
        if (r.validFrom() != null && r.validTo() != null && !r.validTo().isAfter(r.validFrom())) {
            throw new BadRequestException("validTo must be after validFrom");
        }
    }

    private void applyTargets(PromoCodeRequest r, PromoCode p) {
        p.getApplicableCategories().clear();
        if (r.applicableCategoryIds() != null) {
            for (Long id : r.applicableCategoryIds()) {
                p.getApplicableCategories().add(categories.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("No category with id " + id)));
            }
        }
        p.getApplicableProducts().clear();
        if (r.applicableProductIds() != null) {
            for (Long id : r.applicableProductIds()) {
                p.getApplicableProducts().add(products.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("No product with id " + id)));
            }
        }
    }
}
