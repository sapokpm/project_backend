package com.example.shop.specification;

import com.example.shop.model.entity.Product;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;

/** Each method returns null-safe building blocks; the service ANDs together the ones the caller asked for. */
public final class ProductSpecifications {
    private ProductSpecifications() {
    }

    public static Specification<Product> isActive() {
        return (root, q, cb) -> cb.isTrue(root.get("isActive"));
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return (root, q, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> hasBrand(Long brandId) {
        return (root, q, cb) -> cb.equal(root.get("brand").get("id"), brandId);
    }

    public static Specification<Product> isFeatured() {
        return (root, q, cb) -> cb.isTrue(root.get("isFeatured"));
    }

    public static Specification<Product> isNewArrival() {
        return (root, q, cb) -> cb.isTrue(root.get("isNewArrival"));
    }

    public static Specification<Product> hasMinRating(BigDecimal rating) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.<BigDecimal>get("averageRating"), rating);
    }

    public static Specification<Product> hasStock(boolean wanted) {
        return (root, q, cb) -> {
            Expression<Integer> available = cb.diff(root.<Integer>get("stockQuantity"), root.<Integer>get("reservedQuantity"));
            return wanted ? cb.greaterThan(available, 0) : cb.lessThanOrEqualTo(available, 0);
        };
    }

    /** Price filters use what the customer would actually pay: discount price while the discount runs. */
    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, q, cb) -> {
            LocalDateTime now = LocalDateTime.now();
            Predicate discountRunning = cb.and(
                    cb.isNotNull(root.get("discountPrice")),
                    cb.or(cb.isNull(root.get("discountStartDate")), cb.lessThanOrEqualTo(root.<LocalDateTime>get("discountStartDate"), now)),
                    cb.or(cb.isNull(root.get("discountEndDate")), cb.greaterThanOrEqualTo(root.<LocalDateTime>get("discountEndDate"), now)));
            Expression<BigDecimal> price = cb.<BigDecimal>selectCase()
                    .when(discountRunning, root.<BigDecimal>get("discountPrice"))
                    .otherwise(root.<BigDecimal>get("basePrice"));
            Predicate result = cb.conjunction();
            if (min != null) result = cb.and(result, cb.greaterThanOrEqualTo(price, min));
            if (max != null) result = cb.and(result, cb.lessThanOrEqualTo(price, max));
            return result;
        };
    }

    public static Specification<Product> matchesText(String text) {
        String like = "%" + text.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like, '\\'),
                cb.like(cb.lower(root.get("sku")), like, '\\'),
                cb.like(cb.lower(root.get("shortDescription")), like, '\\'));
    }
}
