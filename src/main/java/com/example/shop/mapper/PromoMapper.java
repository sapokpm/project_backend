package com.example.shop.mapper;

import com.example.shop.dto.request.PromoCodeRequest;
import com.example.shop.dto.response.PromoCodeResponse;
import com.example.shop.model.entity.Category;
import com.example.shop.model.entity.Product;
import com.example.shop.model.entity.PromoCode;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class PromoMapper {

    public PromoCodeResponse toResponse(PromoCode p) {
        return new PromoCodeResponse(p.getId(), p.getCode(), p.getDescription(), p.getDiscountType(),
                p.getDiscountValue(), p.getMinOrderAmount(), p.getMaxDiscountAmount(), p.getMaxUsageCount(),
                p.getCurrentUsageCount(), p.getMaxUsagePerUser(), p.getValidFrom(), p.getValidTo(),
                Boolean.TRUE.equals(p.getIsActive()),
                p.getApplicableCategories().stream().map(Category::getId).collect(Collectors.toSet()),
                p.getApplicableProducts().stream().map(Product::getId).collect(Collectors.toSet()));
    }

    /** Scalar fields only; the category and product sets are resolved by the service. */
    public void apply(PromoCodeRequest r, PromoCode p) {
        p.setCode(r.code().trim().toUpperCase());
        p.setDescription(r.description());
        p.setDiscountType(r.discountType());
        p.setDiscountValue(r.discountValue());
        p.setMinOrderAmount(r.minOrderAmount());
        p.setMaxDiscountAmount(r.maxDiscountAmount());
        p.setMaxUsageCount(r.maxUsageCount());
        p.setMaxUsagePerUser(r.maxUsagePerUser());
        p.setValidFrom(r.validFrom());
        p.setValidTo(r.validTo());
        if (r.isActive() != null) p.setIsActive(r.isActive());
    }
}
