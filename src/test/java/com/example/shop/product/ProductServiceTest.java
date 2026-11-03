package com.example.shop.product;

import com.example.shop.dto.request.CreateProductRequest;
import com.example.shop.dto.request.StockUpdateRequest;
import com.example.shop.dto.request.UpdateProductRequest;
import com.example.shop.dto.request.VariantRequest;
import com.example.shop.exception.*;
import com.example.shop.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Rules from Part 11 "ProductService". Rolled back after each test. */
@SpringBootTest
@Transactional
class ProductServiceTest {
    @Autowired ProductService service;
    @Autowired JdbcTemplate jdbc;

    private Long idOf(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    private int reserved(String sku) {
        return jdbc.queryForObject("select reserved_quantity from products where sku = ?", Integer.class, sku);
    }

    private Long categoryId() {
        return jdbc.queryForObject("select id from categories where slug = 'audio'", Long.class);
    }

    private CreateProductRequest newProduct(String name, String sku, String base, String discount) {
        return new CreateProductRequest(name, "d", null, sku, new BigDecimal(base),
                discount == null ? null : new BigDecimal(discount), null, null, 10, null, null, null, null, null,
                categoryId(), null);
    }

    @Test
    void reserveReducesAvailabilityAndRefusesToOversell() {
        Long id = idOf("GWSTRAP"); // stock 3
        service.reserveStock(id, null, 2);
        assertThat(reserved("GWSTRAP")).isEqualTo(2);
        assertThat(service.findById(id).availableQuantity()).isEqualTo(1);
        assertThatThrownBy(() -> service.reserveStock(id, null, 2)).isInstanceOf(InsufficientStockException.class);
        assertThat(reserved("GWSTRAP")).isEqualTo(2); // the failed attempt changed nothing
        service.reserveStock(id, null, 1);
        assertThat(service.findById(id).inStock()).isFalse();
    }

    @Test
    void releaseGivesStockBackButNotMoreThanReserved() {
        Long id = idOf("GWSTRAP");
        service.reserveStock(id, null, 2);
        service.releaseStock(id, null, 2);
        assertThat(reserved("GWSTRAP")).isZero();
        assertThatThrownBy(() -> service.releaseStock(id, null, 1)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void reserveValidatesInput() {
        assertThatThrownBy(() -> service.reserveStock(idOf("GWSTRAP"), null, 0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reserveStock(999999L, null, 1)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void variantStockIsReservedSeparately() {
        Long p = idOf("IPH15");
        var v = service.addVariant(p, new VariantRequest("IPH15-256", "256GB", "{\"storage\":\"256GB\"}",
                new BigDecimal("899"), 2, null));
        service.reserveStock(p, v.id(), 2);
        assertThat(reserved("IPH15")).isZero();
        assertThatThrownBy(() -> service.reserveStock(p, v.id(), 1)).isInstanceOf(InsufficientStockException.class);
        assertThat(service.variants(p)).singleElement().extracting("availableQuantity").isEqualTo(0);
    }

    @Test
    void stockCannotBeSetBelowWhatIsReserved() {
        Long id = idOf("GWSTRAP");
        service.reserveStock(id, null, 3);
        assertThatThrownBy(() -> service.updateStock(id, new StockUpdateRequest(2))).isInstanceOf(BadRequestException.class);
        assertThat(service.updateStock(id, new StockUpdateRequest(10)).availableQuantity()).isEqualTo(7);
    }

    @Test
    void softDeleteHidesButKeepsTheRow() {
        Long id = idOf("IPH15");
        service.softDelete(id);
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from products where id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(service.setActive(id, true).isActive()).isTrue();
        assertThat(service.findById(id).name()).isEqualTo("iPhone 15");
    }

    @Test
    void createBuildsSlugAndRefusesDuplicateSku() {
        var created = service.create(newProduct("Pixel Phone", "PIX-1", "500", null));
        assertThat(created.slug()).isEqualTo("pixel-phone");
        assertThat(created.categoryName()).isEqualTo("Audio");
        var twin = service.create(newProduct("Pixel Phone", "PIX-2", "500", null));
        assertThat(twin.slug()).isEqualTo("pixel-phone-2");
        assertThatThrownBy(() -> service.create(newProduct("Other", "PIX-1", "10", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void discountMustBeBelowBasePrice() {
        assertThatThrownBy(() -> service.create(newProduct("Bad", "BAD-1", "100", "100"))).isInstanceOf(BadRequestException.class);
        Long id = idOf("IPH15");
        assertThatThrownBy(() -> service.update(id, new UpdateProductRequest(null, null, null, null,
                new BigDecimal("900"), null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateChangesOnlyTheGivenFields() {
        Long id = idOf("IPH15");
        var updated = service.update(id, new UpdateProductRequest("iPhone 15 (2026)", null, null, null, null, null, null,
                null, null, null, null, null, null, null));
        assertThat(updated.name()).isEqualTo("iPhone 15 (2026)");
        assertThat(updated.basePrice()).isEqualByComparingTo("799");
        assertThat(updated.sku()).isEqualTo("IPH15");
    }

    @Test
    void ratingIsRecalculatedFromApprovedReviewsOnly() {
        Long id = idOf("GS24U"); // seeded review is PENDING, so no rating yet
        assertThat(service.findById(id).reviewCount()).isZero();
        jdbc.update("update reviews set status = 'APPROVED' where product_id = ?", id);
        service.recalculateRating(id);
        var p = service.findById(id);
        assertThat(p.reviewCount()).isEqualTo(1);
        assertThat(p.averageRating()).isEqualByComparingTo("2.00");
        jdbc.update("update reviews set status = 'REJECTED' where product_id = ?", id);
        service.recalculateRating(id);
        assertThat(service.findById(id).averageRating()).isEqualByComparingTo("0");
    }
}
