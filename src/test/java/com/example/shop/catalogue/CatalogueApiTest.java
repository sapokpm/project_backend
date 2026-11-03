package com.example.shop.catalogue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogueApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private ResultActions ok(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk());
    }

    private Long catId(String slug) {
        return jdbc.queryForObject("select id from categories where slug = ?", Long.class, slug);
    }

    @Test
    void categoryTreeNestsChildrenUnderRoots() throws Exception {
        // roots by sort order: Phones(1), Laptops(3), Audio(4), Accessories(5); Smartphones sits under Phones
        ok("/api/categories").andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].name", contains("Phones", "Laptops", "Audio", "Accessories")))
                .andExpect(jsonPath("$[0].parentId").doesNotExist())
                .andExpect(jsonPath("$[0].children", hasSize(1)))
                .andExpect(jsonPath("$[0].children[0].name").value("Smartphones"))
                .andExpect(jsonPath("$[0].children[0].parentId").value(catId("phones")))
                .andExpect(jsonPath("$[1].children", hasSize(0)));
    }

    @Test
    void inactiveCategoriesLeaveTheTree() throws Exception {
        jdbc.update("update categories set is_active = false where slug = 'smartphones'");
        ok("/api/categories").andExpect(jsonPath("$[0].children", hasSize(0)));
        mvc.perform(get("/api/categories/" + catId("smartphones"))).andExpect(status().isNotFound());
        jdbc.update("update categories set is_active = false where slug = 'audio'");
        ok("/api/categories").andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void oneCategoryByIdOrBySlug() throws Exception {
        Long phones = catId("phones");
        ok("/api/categories/" + phones).andExpect(jsonPath("$.slug").value("phones"))
                .andExpect(jsonPath("$.children[0].slug").value("smartphones"));
        ok("/api/categories/smartphones").andExpect(jsonPath("$.name").value("Smartphones"))
                .andExpect(jsonPath("$.parentId").value(phones));
        mvc.perform(get("/api/categories/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No category with id 999999"));
        mvc.perform(get("/api/categories/no-such-slug")).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories/99999999999999999999")).andExpect(status().isNotFound());
    }

    @Test
    void categoryProductsAreIncludedWithSubcategoriesAndPaged() throws Exception {
        ok("/api/categories/" + catId("phones") + "/products").andExpect(jsonPath("$.totalElements").value(6));
        ok("/api/categories/" + catId("audio") + "/products?size=2").andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/categories/999999/products")).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories/" + catId("audio") + "/products?sort=hack")).andExpect(status().isBadRequest());
    }

    @Test
    void brandsListIsPagedAlphabeticalAndActiveOnly() throws Exception {
        ok("/api/brands").andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].name", contains("Apple", "Samsung", "Sony")));
        jdbc.update("update brands set is_active = false where slug = 'samsung'");
        ok("/api/brands").andExpect(jsonPath("$.content[*].name", contains("Apple", "Sony")));
        ok("/api/brands?sort=password,desc&size=1").andExpect(jsonPath("$.content[0].name").value("Apple"));
    }

    @Test
    void oneBrandAndItsProducts() throws Exception {
        Long sony = jdbc.queryForObject("select id from brands where slug = 'sony'", Long.class);
        ok("/api/brands/" + sony).andExpect(jsonPath("$.name").value("Sony")).andExpect(jsonPath("$.country").value("Japan"));
        ok("/api/brands/" + sony + "/products").andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].brandName", everyItem(is("Sony"))));
        mvc.perform(get("/api/brands/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/brands/999999/products")).andExpect(status().isNotFound());
        mvc.perform(get("/api/brands/abc")).andExpect(status().isBadRequest());
        jdbc.update("update brands set is_active = false where id = ?", sony);
        mvc.perform(get("/api/brands/" + sony)).andExpect(status().isNotFound());
    }
}
