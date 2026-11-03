package com.example.shop.controller;

import com.example.shop.dto.response.ProductResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.dto.response.ProductVariantResponse;
import com.example.shop.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public Page<ProductSummaryResponse> list(
            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Long brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) BigDecimal rating,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return products.search(category, brand, minPrice, maxPrice, inStock, rating, search, pageable);
    }

    @GetMapping("/search")
    public Page<ProductSummaryResponse> search(@RequestParam String q, @PageableDefault(size = 20) Pageable pageable) {
        return products.search(null, null, null, null, null, null, q, pageable);
    }

    @GetMapping("/featured")
    public Page<ProductSummaryResponse> featured(@PageableDefault(size = 20) Pageable pageable) {
        return products.featured(pageable);
    }

    @GetMapping("/new-arrivals")
    public Page<ProductSummaryResponse> newArrivals(@PageableDefault(size = 20) Pageable pageable) {
        return products.newArrivals(pageable);
    }

    @GetMapping("/best-sellers")
    public Page<ProductSummaryResponse> bestSellers(@PageableDefault(size = 20) Pageable pageable) {
        return products.bestSellers(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse one(@PathVariable Long id) {
        return products.findById(id);
    }

    // a handful of variants per product, so a plain list rather than a page
    @GetMapping("/{id}/variants")
    public List<ProductVariantResponse> variants(@PathVariable Long id) {
        return products.variants(id);
    }

    // capped at 20 by the service
    @GetMapping("/{id}/related")
    public List<ProductSummaryResponse> related(@PathVariable Long id, @RequestParam(defaultValue = "8") int limit) {
        return products.related(id, limit);
    }
}
