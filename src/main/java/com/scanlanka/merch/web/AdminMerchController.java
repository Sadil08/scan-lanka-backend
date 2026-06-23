package com.scanlanka.merch.web;

import com.scanlanka.merch.app.MerchService;
import com.scanlanka.merch.app.MerchService.BannerInput;
import com.scanlanka.merch.app.MerchService.BannerView;
import com.scanlanka.merch.app.MerchService.FeaturedEntry;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/merch")
public class AdminMerchController {

    private final MerchService merch;

    public AdminMerchController(MerchService merch) {
        this.merch = merch;
    }

    public record FeaturedRequest(List<FeaturedEntry> items) {}
    public record BannerBody(String linkUrl, int displayOrder, Instant startsAt, Instant endsAt, boolean active) {}

    @GetMapping("/featured")
    public List<FeaturedEntry> featured() {
        return merch.listFeatured();
    }

    @PutMapping("/featured")
    public List<FeaturedEntry> saveFeatured(@RequestBody FeaturedRequest req) {
        return merch.saveFeatured(req.items() == null ? List.of() : req.items());
    }

    @GetMapping("/banners")
    public List<BannerView> banners() {
        return merch.listBanners();
    }

    @PostMapping("/banners")
    public BannerView create(@RequestBody BannerBody body) {
        return merch.createBanner(toInput(body));
    }

    @PutMapping("/banners/{id}")
    public BannerView update(@PathVariable long id, @RequestBody BannerBody body) {
        return merch.updateBanner(id, toInput(body));
    }

    @DeleteMapping("/banners/{id}")
    public void delete(@PathVariable long id) {
        merch.deleteBanner(id);
    }

    @PostMapping("/banners/{id}/image")
    public BannerView image(@PathVariable long id, @RequestParam("file") MultipartFile file) throws IOException {
        return merch.uploadBannerImage(id, file.getBytes());
    }

    private static BannerInput toInput(BannerBody body) {
        return new BannerInput(body.linkUrl(), body.displayOrder(), body.startsAt(), body.endsAt(), body.active());
    }
}
