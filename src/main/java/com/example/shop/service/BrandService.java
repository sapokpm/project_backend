package com.example.shop.service;

import com.example.shop.dto.request.BrandRequest;
import com.example.shop.dto.response.BrandResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BrandService {
    Page<BrandResponse> list(Pageable pageable);

    BrandResponse findById(Long id);

    Page<ProductSummaryResponse> products(Long id, Pageable pageable);

    // ---- staff writes (admin controller later) ----
    BrandResponse create(BrandRequest request);

    BrandResponse update(Long id, BrandRequest request);

    /** Refused while products still use the brand. */
    void delete(Long id);
}
