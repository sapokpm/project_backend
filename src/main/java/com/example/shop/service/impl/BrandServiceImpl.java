package com.example.shop.service.impl;

import com.example.shop.dto.request.BrandRequest;
import com.example.shop.dto.response.BrandResponse;
import com.example.shop.dto.response.ProductSummaryResponse;
import com.example.shop.exception.ResourceInUseException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.CategoryMapper;
import com.example.shop.model.entity.Brand;
import com.example.shop.repository.BrandRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.service.BrandService;
import com.example.shop.service.ProductService;
import com.example.shop.util.SlugUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BrandServiceImpl implements BrandService {
    private final BrandRepository brands;
    private final ProductRepository products;
    private final ProductService productService;
    private final CategoryMapper mapper; // brand mapping lives beside category mapping

    public BrandServiceImpl(BrandRepository brands, ProductRepository products, ProductService productService,
                            CategoryMapper mapper) {
        this.brands = brands;
        this.products = products;
        this.productService = productService;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BrandResponse> list(Pageable pageable) {
        // brands are listed alphabetically; the client does not choose the sort column
        Pageable p = PageRequest.of(pageable.getPageNumber(), Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by("name").and(Sort.by("id")));
        return brands.findByIsActiveTrue(p).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BrandResponse findById(Long id) {
        return mapper.toResponse(activeBrand(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> products(Long id, Pageable pageable) {
        activeBrand(id);
        return productService.search(null, id, null, null, null, null, null, pageable);
    }

    @Override
    @Transactional
    public BrandResponse create(BrandRequest r) {
        Brand b = new Brand();
        mapper.apply(r, b);
        b.setSlug(SlugUtil.uniqueSlug(r.name(), brands::existsBySlug));
        return mapper.toResponse(brands.save(b));
    }

    @Override
    @Transactional
    public BrandResponse update(Long id, BrandRequest r) {
        Brand b = brand(id);
        mapper.apply(r, b);
        return mapper.toResponse(b);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Brand b = brand(id);
        if (products.existsByBrandId(id)) {
            throw new ResourceInUseException("Brand still has products; reassign or deactivate them first");
        }
        brands.delete(b);
    }

    private Brand brand(Long id) {
        return brands.findById(id).orElseThrow(() -> new ResourceNotFoundException("No brand with id " + id));
    }

    private Brand activeBrand(Long id) {
        return brands.findByIdAndIsActiveTrue(id).orElseThrow(() -> new ResourceNotFoundException("No brand with id " + id));
    }
}
