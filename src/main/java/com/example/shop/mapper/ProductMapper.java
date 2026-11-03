package com.example.shop.mapper;

import com.example.shop.dto.request.CreateProductRequest;
import com.example.shop.dto.request.UpdateProductRequest;
import com.example.shop.dto.request.VariantRequest;
import com.example.shop.dto.response.*;
import com.example.shop.model.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;

/** Touches lazy relations: call from inside a service transaction. */
@Component
public class ProductMapper {

    public static BigDecimal currentPrice(Product p) {
        LocalDateTime now = LocalDateTime.now();
        boolean running = p.getDiscountPrice() != null
                && (p.getDiscountStartDate() == null || !p.getDiscountStartDate().isAfter(now))
                && (p.getDiscountEndDate() == null || !p.getDiscountEndDate().isBefore(now));
        return running ? p.getDiscountPrice() : p.getBasePrice();
    }

    public static int available(Product p) {
        return Math.max(0, p.getStockQuantity() - p.getReservedQuantity());
    }

    public ProductResponse toResponse(Product p) {
        BigDecimal current = currentPrice(p);
        Category c = p.getCategory();
        Brand b = p.getBrand();
        return new ProductResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getShortDescription(),
                p.getSku(), p.getBasePrice(), current, current.compareTo(p.getBasePrice()) < 0,
                available(p), available(p) > 0, p.getWeight(), p.getDimensions(),
                Boolean.TRUE.equals(p.getIsActive()), Boolean.TRUE.equals(p.getIsFeatured()),
                Boolean.TRUE.equals(p.getIsNewArrival()), p.getAverageRating(), p.getReviewCount(),
                c.getId(), c.getName(), b == null ? null : b.getId(), b == null ? null : b.getName(),
                p.getImages().stream().sorted(Comparator.comparing(ProductImage::getSortOrder))
                        .map(this::toResponse).toList(),
                p.getSpecifications().stream().sorted(Comparator.comparing(ProductSpecification::getSortOrder))
                        .map(this::toResponse).toList(),
                p.getCreatedAt());
    }

    public ProductSummaryResponse toSummary(Product p) {
        BigDecimal current = currentPrice(p);
        String primary = p.getImages().stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsPrimary()))
                .map(ProductImage::getImageUrl).findFirst()
                .orElseGet(() -> p.getImages().stream().map(ProductImage::getImageUrl).findFirst().orElse(null));
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getBasePrice(), current,
                current.compareTo(p.getBasePrice()) < 0, available(p) > 0, p.getAverageRating(),
                p.getReviewCount(), primary, p.getBrand() == null ? null : p.getBrand().getName());
    }

    public ProductImageResponse toResponse(ProductImage i) {
        return new ProductImageResponse(i.getId(), i.getImageUrl(), i.getThumbnailUrl(), i.getAltText(),
                Boolean.TRUE.equals(i.getIsPrimary()), i.getSortOrder());
    }

    public ProductSpecificationResponse toResponse(ProductSpecification s) {
        return new ProductSpecificationResponse(s.getId(), s.getName(), s.getValue(), s.getGroupName(), s.getSortOrder());
    }

    public ProductVariantResponse toResponse(ProductVariant v) {
        return new ProductVariantResponse(v.getId(), v.getSku(), v.getName(), v.getAttributes(), v.getPrice(),
                Math.max(0, v.getStockQuantity() - v.getReservedQuantity()), v.getImageUrl(),
                Boolean.TRUE.equals(v.getIsActive()));
    }

    /** Category, brand, slug and sku-uniqueness are resolved by the service. */
    public Product toEntity(CreateProductRequest r) {
        Product p = new Product();
        p.setName(r.name());
        p.setDescription(r.description());
        p.setShortDescription(r.shortDescription());
        p.setSku(r.sku());
        p.setBasePrice(r.basePrice());
        p.setDiscountPrice(r.discountPrice());
        p.setDiscountStartDate(r.discountStartDate());
        p.setDiscountEndDate(r.discountEndDate());
        p.setStockQuantity(r.stockQuantity());
        if (r.lowStockThreshold() != null) p.setLowStockThreshold(r.lowStockThreshold());
        p.setWeight(r.weight());
        p.setDimensions(r.dimensions());
        p.setIsFeatured(Boolean.TRUE.equals(r.isFeatured()));
        p.setIsNewArrival(Boolean.TRUE.equals(r.isNewArrival()));
        return p;
    }

    /** Partial update: only non-null fields change. Category and brand are resolved by the service. */
    public void apply(UpdateProductRequest r, Product p) {
        if (r.name() != null) p.setName(r.name());
        if (r.description() != null) p.setDescription(r.description());
        if (r.shortDescription() != null) p.setShortDescription(r.shortDescription());
        if (r.basePrice() != null) p.setBasePrice(r.basePrice());
        if (r.discountPrice() != null) p.setDiscountPrice(r.discountPrice());
        if (r.discountStartDate() != null) p.setDiscountStartDate(r.discountStartDate());
        if (r.discountEndDate() != null) p.setDiscountEndDate(r.discountEndDate());
        if (r.lowStockThreshold() != null) p.setLowStockThreshold(r.lowStockThreshold());
        if (r.weight() != null) p.setWeight(r.weight());
        if (r.dimensions() != null) p.setDimensions(r.dimensions());
        if (r.isFeatured() != null) p.setIsFeatured(r.isFeatured());
        if (r.isNewArrival() != null) p.setIsNewArrival(r.isNewArrival());
    }

    public ProductVariant toEntity(VariantRequest r) {
        ProductVariant v = new ProductVariant();
        apply(r, v);
        return v;
    }

    public void apply(VariantRequest r, ProductVariant v) {
        v.setSku(r.sku());
        v.setName(r.name());
        v.setAttributes(r.attributes());
        v.setPrice(r.price());
        v.setStockQuantity(r.stockQuantity());
        v.setImageUrl(r.imageUrl());
    }
}
