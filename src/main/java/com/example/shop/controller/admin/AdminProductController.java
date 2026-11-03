package com.example.shop.controller.admin;

import com.example.shop.dto.request.CreateProductRequest;
import com.example.shop.dto.request.StockUpdateRequest;
import com.example.shop.dto.request.UpdateProductRequest;
import com.example.shop.dto.request.VariantRequest;
import com.example.shop.dto.response.ProductResponse;
import com.example.shop.dto.response.ProductVariantResponse;
import com.example.shop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Catalogue management: ADMIN only (MANAGER is for orders). */
@RestController
@RequestMapping("/api/admin/products")
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {
    private final ProductService products;

    public AdminProductController(ProductService products) {
        this.products = products;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    /** Soft delete: the product is hidden, not removed, because orders still point at it. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        products.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    public ProductResponse stock(@PathVariable Long id, @Valid @RequestBody StockUpdateRequest request) {
        return products.updateStock(id, request);
    }

    @PatchMapping("/{id}/activate")
    public ProductResponse activate(@PathVariable Long id) {
        return products.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivate(@PathVariable Long id) {
        return products.setActive(id, false);
    }

    @PostMapping("/{id}/variants")
    public ResponseEntity<ProductVariantResponse> addVariant(@PathVariable Long id, @Valid @RequestBody VariantRequest request) {
        ProductVariantResponse created = products.addVariant(id, request);
        return ResponseEntity.created(URI.create("/api/products/" + id + "/variants")).body(created);
    }

    @PutMapping("/{id}/variants/{variantId}")
    public ProductVariantResponse updateVariant(@PathVariable Long id, @PathVariable Long variantId,
                                                @Valid @RequestBody VariantRequest request) {
        return products.updateVariant(id, variantId, request);
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
        products.deleteVariant(id, variantId);
        return ResponseEntity.noContent().build();
    }
}
