package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.domain.SpecOption;
import com.scanlanka.catalog.infra.ProductImageRepository;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.web.dto.ProductResponses.AdminProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.AdminVariantDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.DeliveryAttrsDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.AdminProductRowDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.CategoryAdminDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.LorryPricingRowDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.OptionDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.SpecGroupDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Admin catalog reads and category maintenance (01 §3). Includes hidden/archived products. */
@Service
@Transactional(readOnly = true)
public class AdminCatalogService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;
    private final ProductImageRepository images;
    private final CatalogCacheEvictor cacheEvictor;

    public AdminCatalogService(ProductRepository products, ProductVariantRepository variants,
                               SpecGroupRepository groups, SpecOptionRepository options,
                               ProductImageRepository images, CatalogCacheEvictor cacheEvictor) {
        this.products = products;
        this.variants = variants;
        this.groups = groups;
        this.options = options;
        this.images = images;
        this.cacheEvictor = cacheEvictor;
    }

    public List<AdminProductRowDTO> listProducts() {
        return products.findAllByOrderByNameAsc().stream().map(this::toRow).toList();
    }

    public AdminProductDetailDTO getProduct(long id) {
        Product p = products.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        return toDetail(p);
    }

    /**
     * One row per purchasable unit (product if single-priced, else each active variant) across the
     * WHOLE catalog, flattened for the admin lorry-pricing overview table (08/17, owner 2026-07-07) —
     * so every size's Colombo/Suburb/Outer cell is visible/editable in one place instead of opening
     * each product individually.
     */
    public List<LorryPricingRowDTO> listLorryPricing() {
        List<LorryPricingRowDTO> rows = new ArrayList<>();
        for (Product p : products.findAllByOrderByNameAsc()) {
            if (p.isArchived()) continue;
            if (p.getPriceMode() == PriceMode.SINGLE) {
                rows.add(new LorryPricingRowDTO(p.getId(), p.getName(), null, null, productDelivery(p)));
                continue;
            }
            for (ProductVariant v : variants.findByProductId(p.getId())) {
                if (!v.isActive()) continue;
                rows.add(new LorryPricingRowDTO(p.getId(), p.getName(), v.getId(),
                    sizeLabel(v.getOptionsSignature()), variantDelivery(v)));
            }
        }
        return rows;
    }

    /** Resolves a variant's (usually single-option) signature to its human label, e.g. "6 x 4". */
    private String sizeLabel(String optionsSignature) {
        if (optionsSignature == null || optionsSignature.isBlank()) return null;
        return Arrays.stream(optionsSignature.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(this::parseOptionId)
            .filter(id -> id != null)
            .map(id -> options.findById(id).map(SpecOption::getValue).orElse(null))
            .filter(v -> v != null)
            .collect(Collectors.joining(" / "));
    }

    private Long parseOptionId(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<CategoryAdminDTO> listCategories() {
        return products.countProductsByCategory().stream()
            .map(row -> new CategoryAdminDTO((String) row[0], ((Number) row[1]).longValue()))
            .toList();
    }

    @Transactional
    public int renameCategory(String from, String to) {
        String oldName = from.trim();
        String newName = to.trim();
        if (oldName.isEmpty() || newName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY");
        }
        if (oldName.equalsIgnoreCase(newName)) {
            return 0;
        }
        if (products.countProductsByCategory().stream()
            .anyMatch(row -> newName.equalsIgnoreCase((String) row[0]) && !oldName.equalsIgnoreCase((String) row[0]))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CATEGORY_EXISTS");
        }
        int updated = products.renameCategory(oldName, newName);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND");
        }
        cacheEvictor.evictAll();
        return updated;
    }

    private AdminProductRowDTO toRow(Product p) {
        String previewUrl = images.findFirstByProductIdAndPreviewTrue(p.getId())
            .or(() -> images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
                .filter(i -> i.getVariantId() == null).findFirst())
            .map(i -> i.getUrl()).orElse(null);
        boolean single = p.getPriceMode() == PriceMode.SINGLE;
        return new AdminProductRowDTO(
            p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getCategory(),
            p.getPriceMode().name(), p.isActive(), p.isArchived(),
            single ? p.getStockQty() : null,
            single ? p.getSinglePriceCents() : null,
            single ? null : p.getPriceRangeMinCents(),
            single ? null : p.getPriceRangeMaxCents(),
            previewUrl);
    }

    private AdminProductDetailDTO toDetail(Product p) {
        List<SpecGroupDTO> specGroups = groups.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(this::toGroupDto).toList();
        List<ProductVariant> activeVariants = variants.findByProductId(p.getId()).stream()
            .filter(ProductVariant::isActive).toList();
        List<AdminVariantDTO> variantDtos = activeVariants.stream()
            .map(v -> new AdminVariantDTO(v.getId(), v.getSku(), v.getPriceCents(),
                v.getOptionsSignature(), StockAvailability.fromQty(v.getStockQty()), variantDelivery(v)))
            .toList();
        List<String> imageUrls = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(i -> i.getUrl()).toList();
        return new AdminProductDetailDTO(
            p.getId(), p.getName(), p.getSlug(), p.getSku(), p.getDescription(), p.getDetails(),
            p.getCategory(), p.getHandlingClass().name(), p.getParentProductId(),
            p.isActive(), p.isArchived(), p.getPriceMode().name(),
            p.getSinglePriceCents(), p.getStockQty(), p.getDisplayOrder(), productDelivery(p),
            imageUrls, specGroups, variantDtos);
    }

    private static DeliveryAttrsDTO productDelivery(Product p) {
        return new DeliveryAttrsDTO(tierName(p.getBoardSizeTier()), p.getLorryColomboCents(), p.getLorrySuburbCents(),
            p.getLorryOuterCents(),
            p.getLorryColomboGateCents(), p.getLorrySuburbGateCents(), p.getLorryOuterGateCents(),
            p.isLorryColomboEnabled(), p.isLorrySuburbEnabled(), p.isLorryOuterEnabled(),
            p.isLorryOuterWhatsapp(), p.isCourierOuterBlocked(), p.isCourierEnabled(), p.isWhatsappOnly());
    }

    private static DeliveryAttrsDTO variantDelivery(ProductVariant v) {
        return new DeliveryAttrsDTO(tierName(v.getBoardSizeTier()), v.getLorryColomboCents(), v.getLorrySuburbCents(),
            v.getLorryOuterCents(),
            v.getLorryColomboGateCents(), v.getLorrySuburbGateCents(), v.getLorryOuterGateCents(),
            v.isLorryColomboEnabled(), v.isLorrySuburbEnabled(), v.isLorryOuterEnabled(),
            v.isLorryOuterWhatsapp(), v.isCourierOuterBlocked(), v.isCourierEnabled(), v.isWhatsappOnly());
    }

    private static String tierName(com.scanlanka.checkout.domain.BoardSizeTier tier) {
        return tier != null ? tier.name() : null;
    }

    private SpecGroupDTO toGroupDto(SpecGroup g) {
        List<OptionDTO> opts = options.findBySpecGroupIdOrderByDisplayOrderAsc(g.getId()).stream()
            .map(o -> new OptionDTO(o.getId(), o.getValue()))
            .collect(Collectors.toList());
        return new SpecGroupDTO(g.getId(), g.getName(), g.isPriceAffecting(), opts);
    }
}
