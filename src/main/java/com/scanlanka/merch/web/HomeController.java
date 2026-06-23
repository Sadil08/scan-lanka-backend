package com.scanlanka.merch.web;

import com.scanlanka.merch.app.MerchService;
import com.scanlanka.merch.app.MerchService.BannerInput;
import com.scanlanka.merch.app.MerchService.FeaturedEntry;
import com.scanlanka.merch.app.MerchService.HomeView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HomeController {

    private final MerchService merch;

    public HomeController(MerchService merch) {
        this.merch = merch;
    }

    @GetMapping("/home")
    public HomeView home() {
        return merch.home();
    }
}
