package com.example.shop.controller.admin;

import com.example.shop.dto.request.BrandRequest;
import com.example.shop.dto.response.BrandResponse;
import com.example.shop.service.BrandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/brands")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBrandController {
    private final BrandService brands;

    public AdminBrandController(BrandService brands) {
        this.brands = brands;
    }

    @PostMapping
    public ResponseEntity<BrandResponse> create(@Valid @RequestBody BrandRequest request) {
        BrandResponse created = brands.create(request);
        return ResponseEntity.created(URI.create("/api/brands/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public BrandResponse update(@PathVariable Long id, @Valid @RequestBody BrandRequest request) {
        return brands.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        brands.delete(id);
        return ResponseEntity.noContent().build();
    }
}
