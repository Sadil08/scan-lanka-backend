package com.scanlanka.catalog.app;

import com.scanlanka.catalog.domain.PriceMode;
import com.scanlanka.catalog.domain.Product;
import com.scanlanka.catalog.domain.ProductVariant;
import com.scanlanka.catalog.domain.SpecGroup;
import com.scanlanka.catalog.infra.ParentProductRepository;
import com.scanlanka.catalog.infra.ProductBrowseQueries;
import com.scanlanka.catalog.infra.ProductImageRepository;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.infra.ProductVariantRepository;
import com.scanlanka.catalog.infra.SpecGroupRepository;
import com.scanlanka.catalog.infra.SpecOptionRepository;
import com.scanlanka.catalog.web.dto.ProductResponses.CatalogFacetsDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.CategoryCountDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.OptionDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ParentFacetDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductDetailDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.ResolveVariantResponse;
import com.scanlanka.catalog.web.dto.ProductResponses.SpecGroupDTO;
import com.scanlanka.catalog.web.dto.ProductResponses.VariantDTO;
import org.springframework.cache.annotation.Cacheable;
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
    private final ProductBrowseQueries browseQueries;
    private final ParentProductRepository parents;
    private final ProductVariantRepository variants;
    private final SpecGroupRepository groups;
    private final SpecOptionRepository options;
    private final ProductImageRepository images;
    private final VariantService variantService;

    public ProductQueryService(ProductRepository products, ProductBrowseQueries browseQueries,
                               ParentProductRepository parents, ProductVariantRepository variants,
                               SpecGroupRepository groups, SpecOptionRepository options,
                               ProductImageRepository images, VariantService variantService) {
        this.products = products;
        this.browseQueries = browseQueries;
        this.parents = parents;
        this.variants = variants;
        this.groups = groups;
        this.options = options;
        this.images = images;
        this.variantService = variantService;
    }

    @Cacheable(value = "catalog-list", key = "#filters.cacheKey() + '-' + #pageable.pageNumber + '-' + #pageable.pageSize")
    public Page<ProductChipDTO> list(BrowseFilters filters, Pageable pageable) {
        return browseQueries.browse(filters, pageable).map(this::toChip);
    }

    @Cacheable(value = "catalog-facets")
    public CatalogFacetsDTO facets() {
        List<ParentFacetDTO> parentFacets = products.findDistinctVisibleParentIds().stream()
            .map(parents::findById)
            .flatMap(java.util.Optional::stream)
            .map(pp -> new ParentFacetDTO(pp.getId(), pp.getName(), pp.getSlug()))
            .toList();
        return new CatalogFacetsDTO(parentFacets, products.findDistinctVisibleCategories());
    }

    @Cacheable(value = "catalog-facets", key = "'category-counts'")
    public List<CategoryCountDTO> categoryCounts() {
        return products.countVisibleProductsByCategory().stream()
            .map(row -> new CategoryCountDTO((String) row[0], ((Number) row[1]).longValue()))
            .toList();
    }

    public DetailView detail(String slug) {
        Product p = products.findBySlugAndActiveTrueAndArchivedFalse(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        String etag = "\"" + p.getId() + "-" + p.getUpdatedAt().toEpochMilli() + "\"";
        return new DetailView(toDetail(p), etag);
    }

    /** Wishlist list projection — preserves order, drops hidden/archived products. */
    public List<ProductChipDTO> chipsForVisibleProductIds(List<Long> productIdsInOrder) {
        List<ProductChipDTO> result = new java.util.ArrayList<>();
        for (Long id : productIdsInOrder) {
            products.findById(id)
                .filter(p -> p.isActive() && !p.isArchived())
                .map(this::toChip)
                .ifPresent(result::add);
        }
        return result;
    }

    /** Resolve the variant for a selected set of price-affecting option ids (server-authoritative price). */
    public ResolveVariantResponse resolveVariant(Long productId, Collection<Long> selectedOptionIds) {
        if (selectedOptionIds == null || selectedOptionIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INCOMPLETE_SELECTION");
        }
        String signature = variantService.signature(selectedOptionIds);
        ProductVariant v = variants.findByProductIdAndOptionsSignature(productId, signature)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_OR_INCOMPLETE_SELECTION"));
        return new ResolveVariantResponse(v.getId(), v.getSku(), v.getPriceCents(),
            StockAvailability.fromQty(v.getStockQty()));
    }

    public record DetailView(ProductDetailDTO dto, String etag) {}

    // --- mappers ---

    private ProductDetailDTO toDetail(Product p) {
        List<SpecGroupDTO> specGroups = groups.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(this::toGroupDto).toList();
        List<ProductVariant> activeVariants = variants.findByProductId(p.getId()).stream()
            .filter(ProductVariant::isActive).toList();
        List<VariantDTO> variantDtos = activeVariants.stream()
            .map(v -> new VariantDTO(v.getId(), v.getSku(), v.getPriceCents(),
                v.getOptionsSignature(), StockAvailability.fromQty(v.getStockQty())))
            .toList();
        List<String> imageUrls = images.findByProductIdOrderByDisplayOrderAsc(p.getId()).stream()
            .map(i -> i.getUrl()).toList();
        String avail = p.getPriceMode() == PriceMode.SINGLE
            ? StockAvailability.fromQty(p.getStockQty())
            : StockAvailability.fromVariants(activeVariants);

        return new ProductDetailDTO(p.getId(), p.getSlug(), p.getName(), p.getDescription(), p.getDetails(),
            p.getPriceMode().name(), p.getSinglePriceCents(), p.getPriceRangeMinCents(), p.getPriceRangeMaxCents(),
            avail, imageUrls, specGroups, variantDtos);
    }

    private ProductChipDTO toChip(Product p) {
        String previewUrl = images.findFirstByProductIdAndPreviewTrue(p.getId())
            .map(i -> i.getUrl()).orElse(null);
        boolean single = p.getPriceMode() == PriceMode.SINGLE;
        String avail;
        if (single) {
            avail = StockAvailability.fromQty(p.getStockQty());
        } else {
            avail = StockAvailability.fromVariants(
                variants.findByProductId(p.getId()).stream().filter(ProductVariant::isActive).toList());
        }
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
}
