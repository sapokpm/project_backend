package com.example.shop.product;

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

/** Runs against the seeded database. @Transactional rolls back anything a test changes. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private ResultActions ok(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk());
    }

    private Long idOf(String sku) {
        return jdbc.queryForObject("select id from products where sku = ?", Long.class, sku);
    }

    @Test
    void listReturnsSeededProductsPaginatedAndPublic() throws Exception {
        ok("/api/products").andExpect(jsonPath("$.totalElements").value(20))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.content[0].currentPrice").exists());
        ok("/api/products?size=5&page=1").andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.number").value(1)).andExpect(jsonPath("$.totalPages").value(4));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        ok("/api/products?size=100000").andExpect(jsonPath("$.size").value(lessThanOrEqualTo(100)));
    }

    @Test
    void summaryNeverExposesInternals() throws Exception {
        String json = ok("/api/products?size=1").andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).doesNotContain("reservedQuantity").doesNotContain("stockQuantity");
    }

    @Test
    void filterByCategoryIncludesSubcategories() throws Exception {
        Long phones = jdbc.queryForObject("select id from categories where slug = 'phones'", Long.class);
        // Phones has no products of its own; its child Smartphones has 6
        ok("/api/products?category=" + phones).andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        mvc.perform(get("/api/products?category=999999")).andExpect(status().isNotFound());
    }

    @Test
    void filterByBrand() throws Exception {
        Long apple = jdbc.queryForObject("select id from brands where slug = 'apple'", Long.class);
        Integer expected = jdbc.queryForObject("select count(*) from products where brand_id = ?", Integer.class, apple);
        ok("/api/products?brand=" + apple).andExpect(jsonPath("$.totalElements").value(expected));
    }

    @Test
    void priceFilterUsesTheDiscountedPriceWhileItRuns() throws Exception {
        // iPhone 15: base 799, discount 749 running. 760 upper bound includes it only via the discount.
        ok("/api/products?maxPrice=760&minPrice=740&search=iphone 15")
                .andExpect(jsonPath("$.content[?(@.name=='iPhone 15')]").exists());
        ok("/api/products?minPrice=100000").andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products?minPrice=500&maxPrice=100")).andExpect(status().isBadRequest());
    }

    @Test
    void inStockAccountsForReservations() throws Exception {
        jdbc.update("update products set reserved_quantity = stock_quantity where sku = 'GWSTRAP'");
        ok("/api/products?inStock=false").andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Galaxy Watch Strap"));
        ok("/api/products?inStock=true").andExpect(jsonPath("$.totalElements").value(19));
    }

    @Test
    void ratingAndSearchFilters() throws Exception {
        ok("/api/products?rating=5").andExpect(jsonPath("$.content[*].averageRating", everyItem(greaterThanOrEqualTo(5.0))));
        ok("/api/products?search=galaxy").andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.content[*].name", everyItem(containsStringIgnoringCase("galaxy"))));
        ok("/api/products/search?q=AIRPODS").andExpect(jsonPath("$.totalElements").value(2));
        // LIKE wildcards in user input are literal, not patterns
        ok("/api/products?search=%25").andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products/search")).andExpect(status().isBadRequest());
    }

    @Test
    void sortingIsWhitelisted() throws Exception {
        ok("/api/products?sort=price,desc&size=3").andExpect(jsonPath("$.content[0].name").value("MacBook Pro 14"));
        ok("/api/products?sort=name,asc&size=1").andExpect(jsonPath("$.content[0].name").value("AirPods Max"));
        mvc.perform(get("/api/products?sort=password,asc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot sort by")));
        mvc.perform(get("/api/products?sort=reservedQuantity")).andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsTheProductAndCountsAView() throws Exception {
        Long id = idOf("IPH15");
        Long before = jdbc.queryForObject("select view_count from products where id = ?", Long.class, id);
        ok("/api/products/" + id).andExpect(jsonPath("$.name").value("iPhone 15"))
                .andExpect(jsonPath("$.currentPrice").value(749.0)).andExpect(jsonPath("$.basePrice").value(799.0))
                .andExpect(jsonPath("$.onSale").value(true)).andExpect(jsonPath("$.categoryName").value("Smartphones"))
                .andExpect(jsonPath("$.brandName").value("Apple")).andExpect(jsonPath("$.images", hasSize(1)))
                .andExpect(jsonPath("$.specifications", hasSize(1))).andExpect(jsonPath("$.inStock").value(true));
        Long after = jdbc.queryForObject("select view_count from products where id = ?", Long.class, id);
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void unknownBadAndInactiveProductsAreRefused() throws Exception {
        mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No product with id 999999"));
        mvc.perform(get("/api/products/abc")).andExpect(status().isBadRequest());
        Long id = idOf("IPH15");
        jdbc.update("update products set is_active = false where id = ?", id);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/" + id + "/variants")).andExpect(status().isNotFound());
        ok("/api/products").andExpect(jsonPath("$.totalElements").value(19));
    }

    @Test
    void variantsAndRelated() throws Exception {
        Long id = idOf("IPH15");
        jdbc.update("insert into product_variants (sku, name, price, stock_quantity, product_id) values ('IPH15-128','128GB',799,5,?), ('IPH15-OFF','Hidden',799,5,?)", id, id);
        jdbc.update("update product_variants set is_active = false where sku = 'IPH15-OFF'");
        ok("/api/products/" + id + "/variants").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sku").value("IPH15-128")).andExpect(jsonPath("$[0].availableQuantity").value(5));
        ok("/api/products/" + id + "/related").andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].name", not(hasItem("iPhone 15"))));
        ok("/api/products/" + id + "/related?limit=2").andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void featuredNewArrivalsAndBestSellers() throws Exception {
        ok("/api/products/featured").andExpect(jsonPath("$.totalElements").value(5));
        ok("/api/products/new-arrivals").andExpect(jsonPath("$.totalElements").value(4));
        jdbc.update("update products set sales_count = 50 where sku = 'WH1000XM5'");
        jdbc.update("update products set sales_count = 20 where sku = 'APP2'");
        ok("/api/products/best-sellers?size=2").andExpect(jsonPath("$.content[0].name").value("Sony WH-1000XM5"))
                .andExpect(jsonPath("$.content[1].name").value("AirPods Pro 2"));
    }
}
