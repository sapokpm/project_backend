package com.example.shop.controller;

import com.example.shop.dto.response.BrandResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.service.BrandService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/brands")
public class BrandController {
    private final BrandService brands;

    public BrandController(BrandService brands) {
        this.brands = brands;
    }

    @GetMapping
    public Page<BrandResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        return brands.list(pageable);
    }

    @GetMapping("/{id}")
    public BrandResponse one(@PathVariable Long id) {
        return brands.findById(id);
    }

    @GetMapping("/{id}/products")
    public Page<ProductSummaryResponse> products(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return brands.products(id, pageable);
    }
}
