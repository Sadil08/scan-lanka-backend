package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.infra.ProductImageRepository;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.web.dto.ProductResponses.OptionDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantResponse;
import com.scanlanka.catalog.web.dto.ProductResponses.SpecGroupDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Public storefront reads (02-storefront-browse). Only visible products; prices server-supplied. */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;
    private final ProductImageRepository images;
    private final VariantService variantService;

    public ProductQueryService(ProductRepository products, ProductVariantRepository variants,
                               SpecGroupRepository groups, SpecOptionRepository options,
                               ProductImageRepository images, VariantService variantService) {
        this.products = products;
        this.variants = variants;
        this.groups = groups;
        this.options = options;
        this.images = images;
        this.variantService = variantService;
    }

    public Page<ProductChipDTO> list(Pageable pageable) {
        return products.findByActiveTrueAndArchivedFalse(pageable).map(this::toChip);
    }

    public ProductDetailDTO detail(String slug) {
        Product p = products.findBySlugAndActiveTrueAndArchivedFalse(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));

        List<SpecGroupDTO> specGroups = groups.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(this::toGroupDto).toList();
        List<VariantDTO> variantDtos = variants.findByProductId(p.getId()).stream()
            .filter(ProductVariant::isActive)
            .map(v -> new VariantDTO(v.getId(), v.getSku(), v.getPriceCents(),
                v.getOptionsSignature(), availability(v.getStockQty())))
            .toList();
        List<String> imageUrls = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(i -> i.getUrl()).toList();

        return new ProductDetailDTO(p.getId(), p.getSlug(), p.getName(), p.getDescription(), p.getDetails(),
            p.getPriceMode().name(), p.getSinglePriceCents(), p.getPriceRangeMinCents(), p.getPriceRangeMaxCents(),
            imageUrls, specGroups, variantDtos);
    }

    /** Resolve the variant for a selected set of price-affecting option ids (server-authoritative price). */
    public ResolveVariantResponse resolveVariant(Long productId, Collection<Long> selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INCOMPLETE_SELECTION");
        }
        String signature = variantService.signature(selectedOptionIds);
        ProductVariant v = variants.findByProductIdAndOptionsSignature(productId, signature)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_INCOMPLETE_SELECTION"));
        return new ResolveVariantResponse(v.getId(), v.getSku(), v.getPriceCents(), availability(v.getStockQty()));
    }

    // --- mappers ---

    private ProductChipDTO toChip(Product p) {
        String previewUrl = images.findFirstByProductIdAndPreviewTrue(p.getId())
            .map(i -> i.getUrl()).orElse(null);
        boolean single = p.getPriceMode() == PriceMode.SINGLE;
        String avail = single ? availability(p.getStockQty()) : "IN_STOCK";
        return new ProductChipDTO(p.getId(), p.getSlug(), p.getName(), previewUrl,
            p.getPriceMode().name(),
            single ? p.getSinglePriceCents() : null,
            single ? null : p.getPriceRangeMinCents(),
            single ? null : p.getPriceRangeMaxCents(),
            avail);
    }

    private SpecGroupDTO toGroupDto(SpecGroup g) {
        List<OptionDTO> opts = options.findBySpecGroupIdOrderByDisplayOrderAsc(g.getId()).stream()
            .map(o -> new OptionDTO(o.getId(), o.getValue()))
            .collect(Collectors.toList());
        return new SpecGroupDTO(g.getId(), g.getName(), g.isPriceAffecting(), opts);
    }

    private static String availability(Integer stock) {
        if (stock == null) return "IN_STOCK";   // unlimited
        return stock <= 0 ? "OUT_OF_STOCK" : "IN_STOCK";
    }
}
