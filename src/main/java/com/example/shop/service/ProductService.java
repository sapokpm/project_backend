package com.example.shop.service;

import com.example.shop.dto.request.*;
import com.example.shop.dto.response.ProductResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.dto.response.ProductVariantResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {
    // ---- public reads: only active products are ever visible ----
    Page<ProductSummaryResponse> search(Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice,
                                        Boolean inStock, BigDecimal minRating, String text, Pageable pageable);

    ProductResponse findById(Long id);

    List<ProductVariantResponse> variants(Long productId);

    List<ProductSummaryResponse> related(Long productId, int limit);

    Page<ProductSummaryResponse> featured(Pageable pageable);

    Page<ProductSummaryResponse> newArrivals(Pageable pageable);

    Page<ProductSummaryResponse> bestSellers(Pageable pageable);

    // ---- staff writes (exposed by the admin controller later) ----
    ProductResponse create(CreateProductRequest request);

    ProductResponse update(Long id, UpdateProductRequest request);

    /** Soft delete: isActive = false, the row stays (orders still point at it). */
    void softDelete(Long id);

    ProductResponse setActive(Long id, boolean active);

    ProductResponse updateStock(Long id, StockUpdateRequest request);

    ProductVariantResponse addVariant(Long productId, VariantRequest request);

    ProductVariantResponse updateVariant(Long productId, Long variantId, VariantRequest request);

    void deleteVariant(Long productId, Long variantId);

    // ---- stock reservation (used by cart and order services) ----
    /** Reserves qty of the product, or of the variant when variantId is given. Throws InsufficientStockException. */
    void reserveStock(Long productId, Long variantId, int quantity);

    void releaseStock(Long productId, Long variantId, int quantity);

    /** An order ships: the reserved units leave stock for good. */
    void commitStock(Long productId, Long variantId, int quantity);

    /** Recomputes averageRating and reviewCount from APPROVED reviews. Call after a review is approved. */
    void recalculateRating(Long productId);
}
