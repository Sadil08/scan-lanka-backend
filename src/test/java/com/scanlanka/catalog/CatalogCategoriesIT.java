package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public category counts for storefront browse (02 §3). */
class CatalogCategoriesIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;

    @Test
    void categoriesReturnsCountsForActiveProducts() throws Exception {
        productService.create(new CreateProductRequest(
            null, "Cat A " + System.nanoTime(), null, null, null, "Boards", null, 1, 1000L,
            List.of(), List.of()));
        productService.create(new CreateProductRequest(
            null, "Cat B " + System.nanoTime(), null, null, null, "Boards", null, 1, 2000L,
            List.of(), List.of()));

        mvc.perform(get("/api/catalog/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name=='Boards')].count").value(hasItem(2)));
    }
}
