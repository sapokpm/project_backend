package com.example.shop.service.impl;

import com.example.shop.dto.request.*;
import com.example.shop.dto.response.ProductResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.dto.response.ProductVariantResponse;
import com.example.shop.exception.*;
import com.example.shop.mapper.ProductMapper;
import com.example.shop.model.entity.*;
import com.example.shop.model.enums.ReviewStatus;
import com.example.shop.repository.*;
import com.example.shop.service.ProductService;
import com.example.shop.util.SlugUtil;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.example.shop.specification.ProductSpecifications.*;

@Service
public class ProductServiceImpl implements ProductService {
    /** Client sort keys -> entity properties. Anything else would be a 500 from Hibernate, so it is a 400 here. */
    private static final Map<String, String> SORTABLE = Map.of(
            "name", "name",
            "price", "basePrice",
            "basePrice", "basePrice",
            "createdAt", "createdAt",
            "rating", "averageRating",
            "averageRating", "averageRating",
            "popularity", "salesCount",
            "salesCount", "salesCount",
            "views", "viewCount");

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final ReviewRepository reviews;
    private final ProductMapper mapper;

    public ProductServiceImpl(ProductRepository products, ProductVariantRepository variants,
                              CategoryRepository categories, BrandRepository brands, ReviewRepository reviews,
                              ProductMapper mapper) {
        this.products = products;
        this.variants = variants;
        this.categories = categories;
        this.brands = brands;
        this.reviews = reviews;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ reads

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> search(Long categoryId, Long brandId, BigDecimal minPrice, BigDecimal maxPrice,
                                               Boolean inStock, BigDecimal minRating, String text, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        Specification<Product> spec = isActive();
        if (categoryId != null) {
            // a category includes everything below it: Phones -> Smartphones
            spec = spec.and(inCategories(categoryWithDescendants(categoryId)));
        }
        if (brandId != null) spec = spec.and(hasBrand(brandId));
        if (minPrice != null || maxPrice != null) spec = spec.and(priceBetween(minPrice, maxPrice));
        if (inStock != null) spec = spec.and(hasStock(inStock));
        if (minRating != null) spec = spec.and(hasMinRating(minRating));
        if (text != null && !text.isBlank()) spec = spec.and(matchesText(text));
        return products.findAll(spec, safe(pageable)).map(mapper::toSummary);
    }

    @Override
    @Transactional
    public ProductResponse findById(Long id) {
        activeProduct(id); // 404 for unknown or inactive
        products.incrementViews(id);
        return mapper.toResponse(activeProduct(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> variants(Long productId) {
        activeProduct(productId);
        return variants.findByProductIdAndIsActiveTrue(productId).stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> related(Long productId, int limit) {
        Product p = activeProduct(productId);
        Specification<Product> spec = isActive().and(inCategories(List.of(p.getCategory().getId())))
                .and((root, q, cb) -> cb.notEqual(root.get("id"), productId));
        Pageable top = PageRequest.of(0, Math.min(Math.max(limit, 1), 20),
                Sort.by(Sort.Direction.DESC, "salesCount").and(Sort.by("id")));
        return products.findAll(spec, top).map(mapper::toSummary).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> featured(Pageable pageable) {
        return products.findAll(isActive().and(isFeatured()), safe(pageable)).map(mapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> newArrivals(Pageable pageable) {
        Pageable p = pageable.getSort().isSorted() ? safe(pageable)
                : PageRequest.of(pageable.getPageNumber(), cap(pageable.getPageSize()), Sort.by(Sort.Direction.DESC, "createdAt"));
        return products.findAll(isActive().and(isNewArrival()), p).map(mapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> bestSellers(Pageable pageable) {
        Pageable p = PageRequest.of(pageable.getPageNumber(), cap(pageable.getPageSize()),
                Sort.by(Sort.Direction.DESC, "salesCount").and(Sort.by("id")));
        return products.findAll(isActive(), p).map(mapper::toSummary);
    }

    // ----------------------------------------------------------------- writes

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest r) {
        String sku = r.sku().trim();
        if (products.existsBySku(sku)) {
            throw new DuplicateResourceException("A product with SKU " + sku + " already exists");
        }
        checkDiscount(r.basePrice(), r.discountPrice());
        Product p = mapper.toEntity(r);
        p.setSku(sku);
        p.setSlug(SlugUtil.uniqueSlug(r.name(), products::existsBySlug));
        p.setCategory(category(r.categoryId()));
        p.setBrand(r.brandId() == null ? null : brand(r.brandId()));
        return mapper.toResponse(products.save(p));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest r) {
        Product p = product(id);
        mapper.apply(r, p);
        checkDiscount(p.getBasePrice(), p.getDiscountPrice());
        if (r.categoryId() != null) p.setCategory(category(r.categoryId()));
        if (r.brandId() != null) p.setBrand(brand(r.brandId()));
        return mapper.toResponse(p);
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        product(id).setIsActive(false);
    }

    @Override
    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        Product p = product(id);
        p.setIsActive(active);
        return mapper.toResponse(p);
    }

    @Override
    @Transactional
    public ProductResponse updateStock(Long id, StockUpdateRequest r) {
        Product p = product(id);
        if (r.stockQuantity() < p.getReservedQuantity()) {
            throw new BadRequestException("Stock cannot go below the " + p.getReservedQuantity()
                    + " units already reserved by open orders");
        }
        p.setStockQuantity(r.stockQuantity());
        return mapper.toResponse(p);
    }

    @Override
    @Transactional
    public ProductVariantResponse addVariant(Long productId, VariantRequest r) {
        Product p = product(productId);
        if (variants.existsBySku(r.sku()) || products.existsBySku(r.sku())) {
            throw new DuplicateResourceException("SKU " + r.sku() + " is already in use");
        }
        ProductVariant v = mapper.toEntity(r);
        v.setProduct(p);
        return mapper.toResponse(variants.save(v));
    }

    @Override
    @Transactional
    public ProductVariantResponse updateVariant(Long productId, Long variantId, VariantRequest r) {
        ProductVariant v = variants.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("No variant " + variantId + " on product " + productId));
        if (!v.getSku().equals(r.sku()) && (variants.existsBySku(r.sku()) || products.existsBySku(r.sku()))) {
            throw new DuplicateResourceException("SKU " + r.sku() + " is already in use");
        }
        if (r.stockQuantity() < v.getReservedQuantity()) {
            throw new BadRequestException("Stock cannot go below the " + v.getReservedQuantity() + " units already reserved");
        }
        mapper.apply(r, v);
        return mapper.toResponse(v);
    }

    @Override
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant v = variants.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("No variant " + variantId + " on product " + productId));
        v.setIsActive(false); // soft: carts and orders may still reference it
    }

    // ------------------------------------------------------------ reservation

    @Override
    @Transactional
    public void reserveStock(Long productId, Long variantId, int quantity) {
        requirePositive(quantity);
        int updated = variantId != null ? variants.reserve(variantId, quantity) : products.reserve(productId, quantity);
        if (updated == 0) {
            // distinguish "no such row" from "not enough"
            if (variantId != null ? !variants.existsById(variantId) : !products.existsById(productId)) {
                throw new ResourceNotFoundException(variantId != null ? "No variant with id " + variantId
                        : "No product with id " + productId);
            }
            throw new InsufficientStockException("Not enough stock available to reserve " + quantity + " unit(s)");
        }
    }

    @Override
    @Transactional
    public void releaseStock(Long productId, Long variantId, int quantity) {
        requirePositive(quantity);
        int updated = variantId != null ? variants.release(variantId, quantity) : products.release(productId, quantity);
        if (updated == 0) {
            throw new BadRequestException("Cannot release more stock than is reserved");
        }
    }

    @Override
    @Transactional
    public void commitStock(Long productId, Long variantId, int quantity) {
        requirePositive(quantity);
        int updated = variantId != null ? variants.commit(variantId, quantity) : products.commit(productId, quantity);
        if (updated == 0) {
            throw new BadRequestException("Cannot ship units that are not reserved");
        }
        if (variantId != null) products.addSales(productId, quantity); // a variant sale is a sale of its product
    }

    @Override
    @Transactional
    public void recalculateRating(Long productId) {
        Product p = product(productId);
        Double avg = reviews.averageApprovedRating(productId);
        long count = reviews.countByProductIdAndStatus(productId, ReviewStatus.APPROVED);
        p.setReviewCount((int) count);
        p.setAverageRating(avg == null ? BigDecimal.ZERO : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
    }

    // ---------------------------------------------------------------- helpers

    private Product activeProduct(Long id) {
        return products.findByIdAndIsActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("No product with id " + id));
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("No product with id " + id));
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("No category with id " + id));
    }

    private Brand brand(Long id) {
        return brands.findById(id).orElseThrow(() -> new ResourceNotFoundException("No brand with id " + id));
    }

    private List<Long> categoryWithDescendants(Long rootId) {
        category(rootId); // 404 for an unknown category instead of an empty page
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>(List.of(rootId));
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (ids.contains(id)) continue; // guards against a cycle in bad data
            ids.add(id);
            categories.findByParentCategoryIdAndIsActiveTrueOrderBySortOrderAscIdAsc(id).forEach(c -> queue.add(c.getId()));
        }
        return ids;
    }

    private static void checkDiscount(BigDecimal base, BigDecimal discount) {
        if (discount != null && discount.compareTo(base) >= 0) {
            throw new BadRequestException("discountPrice must be lower than basePrice");
        }
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) throw new BadRequestException("Quantity must be positive");
    }

    private static int cap(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    /** Whitelists sort keys and caps the page size. */
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
        orders.add(Sort.Order.asc("id")); // stable paging when the sort key ties
        return PageRequest.of(pageable.getPageNumber(), cap(pageable.getPageSize()), Sort.by(orders));
    }
}
