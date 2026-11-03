package com.example.shop.catalogue;

import com.example.shop.dto.request.BrandRequest;
import com.example.shop.dto.request.CategoryRequest;
import com.example.shop.dto.request.ReorderCategoriesRequest;
import com.example.shop.exception.BadRequestException;
import com.example.shop.exception.ResourceInUseException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.service.BrandService;
import com.example.shop.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CatalogueServiceTest {
    @Autowired CategoryService categories;
    @Autowired BrandService brands;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;

    private Long catId(String slug) {
        return jdbc.queryForObject("select id from categories where slug = ?", Long.class, slug);
    }

    private CategoryRequest cat(String name, Long parent) {
        return new CategoryRequest(name, null, null, null, parent);
    }

    @Test
    void createBuildsUniqueSlugAndParentLink() {
        var tv = categories.create(cat("Smart TVs", null));
        assertThat(tv.slug()).isEqualTo("smart-tvs");
        assertThat(categories.create(cat("Smart TVs", null)).slug()).isEqualTo("smart-tvs-2");
        var child = categories.create(cat("OLED", tv.id()));
        assertThat(child.parentId()).isEqualTo(tv.id());
        em.flush();
        em.clear(); // a real request reads in a fresh transaction, not the one that created the rows
        assertThat(categories.findById(tv.id()).children()).extracting("name").containsExactly("OLED");
        assertThatThrownBy(() -> categories.create(cat("Orphan", 999999L))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aCategoryCannotBeMovedUnderItself() {
        var a = categories.create(cat("A", null));
        var b = categories.create(cat("B", a.id()));
        var c = categories.create(cat("C", b.id()));
        assertThatThrownBy(() -> categories.update(a.id(), cat("A", a.id()))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> categories.update(a.id(), cat("A", c.id()))).isInstanceOf(BadRequestException.class);
        assertThat(categories.update(c.id(), cat("C", a.id())).parentId()).isEqualTo(a.id());
        assertThat(categories.update(c.id(), cat("C renamed", null)).parentId()).isNull();
    }

    @Test
    void renameKeepsTheSlug() {
        var x = categories.create(cat("Gadgets", null));
        assertThat(categories.update(x.id(), cat("Cool Gadgets", null)).slug()).isEqualTo("gadgets");
    }

    @Test
    void deleteIsRefusedWhileInUse() {
        assertThatThrownBy(() -> categories.delete(catId("phones"))).isInstanceOf(ResourceInUseException.class); // has a child
        assertThatThrownBy(() -> categories.delete(catId("smartphones"))).isInstanceOf(ResourceInUseException.class); // has products
        var empty = categories.create(cat("Empty", null));
        categories.delete(empty.id());
        assertThatThrownBy(() -> categories.findById(empty.id())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reorderChangesTreeOrderAndIsAllOrNothing() {
        categories.reorder(new ReorderCategoriesRequest(List.of(
                new ReorderCategoriesRequest.Item(catId("accessories"), 0),
                new ReorderCategoriesRequest.Item(catId("phones"), 9))));
        assertThat(categories.tree()).extracting("name").containsExactly("Accessories", "Laptops", "Audio", "Phones");
        assertThatThrownBy(() -> categories.reorder(new ReorderCategoriesRequest(List.of(
                new ReorderCategoriesRequest.Item(catId("audio"), 1),
                new ReorderCategoriesRequest.Item(999999L, 2))))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void brandLifecycle() {
        var b = brands.create(new BrandRequest("Nokia", "Phones", null, null, "Finland"));
        assertThat(b.slug()).isEqualTo("nokia");
        assertThat(brands.update(b.id(), new BrandRequest("Nokia Oyj", null, null, null, "Finland")).name()).isEqualTo("Nokia Oyj");
        brands.delete(b.id());
        assertThatThrownBy(() -> brands.findById(b.id())).isInstanceOf(ResourceNotFoundException.class);
        Long apple = jdbc.queryForObject("select id from brands where slug = 'apple'", Long.class);
        assertThatThrownBy(() -> brands.delete(apple)).isInstanceOf(ResourceInUseException.class);
    }
}
