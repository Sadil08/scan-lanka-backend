package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.GroupInput;
import com.scanlanka.catalog.web.dto.ProductRequests.VariantInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public browse API + admin-gate, end-to-end on real Postgres (02-storefront-browse). */
class BrowseIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired SpecGroupRepository groups;
    @Autowired SpecOptionRepository options;

    private Long seedProduct() {
        return productService.create(new CreateProductRequest(
            null, "Carrom Board Browse", null, "desc", "details", "Boards", null, null, null,
            List.of(new GroupInput("Size", true, List.of("Small", "Large"))),
            List.of(new VariantInput(List.of("Small"), 8000, null, 10),
                    new VariantInput(List.of("Large"), 12000, null, 5))));
    }

    @Test
    void listsAndShowsDetailWithVariants() throws Exception {
        Long id = seedProduct();
        Product p = products.findById(id).orElseThrow();

        mvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='" + p.getSlug() + "')].priceMode").value(
                org.hamcrest.Matchers.hasItem("VARIANT")));

        mvc.perform(get("/api/products/" + p.getSlug()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specGroups.length()").value(1))
            .andExpect(jsonPath("$.variants.length()").value(2));
    }

    @Test
    void resolvesVariantPrice() throws Exception {
        Long id = seedProduct();
        // find the "Large" price-affecting option id
        Long sizeGroupId = groups.findByProductIdOrderByDisplayOrderAsc(id).get(0).getId();
        Long largeOptionId = options.findBySpecGroupIdOrderByDisplayOrderAsc(sizeGroupId).stream()
            .filter(o -> o.getValue().equals("Large")).map(SpecOption::getId).findFirst().orElseThrow();

        mvc.perform(post("/api/products/" + id + "/resolve-variant")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOptionIds\":[" + largeOptionId + "]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(12000));
    }

    @Test
    void adminCreateRequiresAuth() throws Exception {
        // /api/admin/** is role-gated — unauthenticated create is rejected (SEC-ADMIN)
        mvc.perform(post("/api/admin/products").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"X\",\"singlePriceCents\":100}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void hiddenProductSlugReturns404() throws Exception {
        mvc.perform(get("/api/products/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    void searchAndFilterProducts() throws Exception {
        Long id = seedProduct();
        Product p = products.findById(id).orElseThrow();

        mvc.perform(get("/api/products").param("q", "Carrom"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='" + p.getSlug() + "')]").exists());

        mvc.perform(get("/api/products").param("q", "nonexistent-xyz"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));

        mvc.perform(get("/api/products").param("category", "Boards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='" + p.getSlug() + "')]").exists());
    }

    @Test
    void facetsEndpointReturnsCategories() throws Exception {
        seedProduct();
        mvc.perform(get("/api/catalog/facets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0]").value("Boards"));
    }
}
