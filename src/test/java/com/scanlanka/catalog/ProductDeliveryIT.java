package com.scanlanka.catalog;

import com.scanlanka.AbstractIntegrationTest;
import com.scanlanka.auth.AuthTestSupport;
import com.scanlanka.auth.infra.AppUserRepository;
import com.scanlanka.catalog.app.ProductService;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.web.dto.ProductRequests.CreateProductRequest;
import com.scanlanka.catalog.web.dto.ProductRequests.GroupInput;
import com.scanlanka.catalog.web.dto.ProductRequests.VariantInput;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin edits per-product + per-size delivery attributes (01 FR-CATALOG-14b/c, 17). ADMIN-gated. */
class ProductDeliveryIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void adminSetsProductLevelDeliveryViaUpdate() throws Exception {
        Long id = productService.create(new CreateProductRequest(
            null, "Single Board " + System.nanoTime(), null, null, null, "Boards", null, 10, 50000L,
            List.of(), List.of()));

        mvc.perform(put("/api/admin/products/" + id).cookie(admin("admin-deliv1@scanlanka.lk"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delivery\":{\"weightKg\":3,\"lorryColomboCents\":80000,"
                    + "\"lorrySuburbCents\":100000,\"lorryOuterCents\":150000,\"whatsappOnly\":false}}"))
            .andExpect(status().isOk());

        Product p = products.findById(id).orElseThrow();
        assertThat(p.getWeightKg()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(p.getLorryColomboCents()).isEqualTo(80000);
        assertThat(p.getLorryOuterCents()).isEqualTo(150000);
        assertThat(p.isWhatsappOnly()).isFalse();
    }

    @Test
    void adminChangesOneSizesLorryChargeOverTime() throws Exception {
        Long id = productService.create(new CreateProductRequest(
            null, "Sized Board " + System.nanoTime(), null, null, null, "Boards", null, null, null,
            List.of(new GroupInput("Size", true, List.of("S", "L"))),
            List.of(new VariantInput(List.of("S"), 250L, null, 5),
                    new VariantInput(List.of("L"), 500L, null, 5))));

        ProductVariant small = variants.findByProductId(id).stream()
            .min((a, b) -> Long.compare(a.getPriceCents(), b.getPriceCents())).orElseThrow();

        mvc.perform(patch("/api/admin/products/" + id + "/variants/" + small.getId() + "/delivery")
                .cookie(admin("admin-deliv2@scanlanka.lk"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delivery\":{\"weightKg\":1,\"lorryColomboCents\":50000}}"))
            .andExpect(status().isOk());

        ProductVariant reloaded = variants.findById(small.getId()).orElseThrow();
        assertThat(reloaded.getLorryColomboCents()).isEqualTo(50000);
        assertThat(reloaded.getWeightKg()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void variantDeliveryEditRequiresAdmin() throws Exception {
        mvc.perform(patch("/api/admin/products/1/variants/1/delivery")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"delivery\":{\"lorryColomboCents\":50000}}"))
            .andExpect(status().isForbidden());
    }

    private Cookie admin(String email) throws Exception {
        AuthTestSupport.seedAdmin(users, encoder, email);
        return AuthTestSupport.loginAdmin(mvc, email, "JBSWY3DPEHPK3PXP");
    }
}
