package com.scanlanka.merch.app;

import com.scanlanka.catalog.app.ImageProcessing;
import com.scanlanka.catalog.app.ProductQueryService;
import com.scanlanka.catalog.infra.ProductRepository;
import com.scanlanka.catalog.web.dto.ProductResponses.ProductChipDTO;
import com.scanlanka.merch.domain.Banner;
import com.scanlanka.merch.domain.FeaturedProduct;
import com.scanlanka.merch.infra.BannerRepository;
import com.scanlanka.merch.infra.FeaturedProductRepository;
import com.scanlanka.shared.storage.ImageStorage;
import com.scanlanka.shared.text.LinkSanitizer;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MerchService {

    private final FeaturedProductRepository featured;
    private final BannerRepository banners;
    private final ProductRepository products;
    private final ProductQueryService chips;
    private final ImageProcessing imageProcessing;
    private final ImageStorage storage;
    private final MerchCacheEvictor cache;

    public MerchService(FeaturedProductRepository featured, BannerRepository banners,
                        ProductRepository products, ProductQueryService chips,
                        ImageProcessing imageProcessing, ImageStorage storage,
                        MerchCacheEvictor cache) {
        this.featured = featured;
        this.banners = banners;
        this.products = products;
        this.chips = chips;
        this.imageProcessing = imageProcessing;
        this.storage = storage;
        this.cache = cache;
    }

    public record FeaturedEntry(long productId, int displayOrder) {}
    public record BannerView(long id, String imageUrl, String linkUrl, int displayOrder,
                             Instant startsAt, Instant endsAt, boolean active) {}
    public record BannerInput(String linkUrl, int displayOrder, Instant startsAt, Instant endsAt, boolean active) {}
    public record HomeView(List<ProductChipDTO> featured, List<BannerView> banners) {}

    @Transactional(readOnly = true)
    @Cacheable("home")
    public HomeView home() {
        List<Long> ids = featured.findAllByOrderByDisplayOrderAsc().stream()
            .map(FeaturedProduct::getProductId).toList();
        List<ProductChipDTO> featuredChips = chips.chipsForVisibleProductIds(ids);
        Instant now = Instant.now();
        List<BannerView> activeBanners = banners.findAllByOrderByDisplayOrderAsc().stream()
            .filter(b -> withinWindow(b, now))
            .map(this::toBannerView)
            .toList();
        return new HomeView(featuredChips, activeBanners);
    }

    @Transactional(readOnly = true)
    public List<FeaturedEntry> listFeatured() {
        return featured.findAllByOrderByDisplayOrderAsc().stream()
            .map(f -> new FeaturedEntry(f.getProductId(), f.getDisplayOrder()))
            .toList();
    }

    @Transactional
    public List<FeaturedEntry> saveFeatured(List<FeaturedEntry> entries) {
        featured.deleteAll();
        for (FeaturedEntry e : entries) {
            var p = products.findById(e.productId())
                .filter(x -> x.isActive() && !x.isArchived())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT"));
            featured.save(new FeaturedProduct(p.getId(), e.displayOrder()));
        }
        cache.evictHome();
        return listFeatured();
    }

    @Transactional(readOnly = true)
    public List<BannerView> listBanners() {
        return banners.findAllByOrderByDisplayOrderAsc().stream().map(this::toBannerView).toList();
    }

    @Transactional
    public BannerView createBanner(BannerInput input) {
        Banner b = new Banner("pending", "/api/media/pending");
        apply(b, input);
        banners.save(b);
        cache.evictHome();
        return toBannerView(b);
    }

    @Transactional
    public BannerView updateBanner(long id, BannerInput input) {
        Banner b = loadBanner(id);
        apply(b, input);
        banners.save(b);
        cache.evictHome();
        return toBannerView(b);
    }

    @Transactional
    public void deleteBanner(long id) {
        banners.delete(loadBanner(id));
        cache.evictHome();
    }

    @Transactional
    public BannerView uploadBannerImage(long id, byte[] bytes) {
        Banner b = loadBanner(id);
        byte[] png = imageProcessing.validateAndReencode(bytes);
        ImageStorage.StoredImage stored = storage.store(png, imageProcessing.outputExtension());
        b.setImage(stored.key(), stored.url());
        banners.save(b);
        cache.evictHome();
        return toBannerView(b);
    }

    static boolean withinWindow(Banner b, Instant now) {
        if (!b.isActive()) return false;
        if (b.getStartsAt() != null && now.isBefore(b.getStartsAt())) return false;
        if (b.getEndsAt() != null && now.isAfter(b.getEndsAt())) return false;
        return b.getImageUrl() != null && !b.getImageUrl().contains("pending");
    }

    private void apply(Banner b, BannerInput input) {
        b.setLinkUrl(LinkSanitizer.sanitize(input.linkUrl()));
        b.setDisplayOrder(input.displayOrder());
        b.setStartsAt(input.startsAt());
        b.setEndsAt(input.endsAt());
        b.setActive(input.active());
    }

    private Banner loadBanner(long id) {
        return banners.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
    }

    private BannerView toBannerView(Banner b) {
        return new BannerView(b.getId(), b.getImageUrl(), b.getLinkUrl(), b.getDisplayOrder(),
            b.getStartsAt(), b.getEndsAt(), b.isActive());
    }
}
