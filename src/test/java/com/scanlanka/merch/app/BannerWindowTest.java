package com.scanlanka.merch.app;

import com.scanlanka.merch.domain.Banner;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BannerWindowTest {

    @Test
    void futureBannerHidden() {
        Banner b = new Banner("k", "/api/media/x");
        b.setActive(true);
        b.setStartsAt(Instant.now().plusSeconds(3600));
        assertThat(MerchService.withinWindow(b, Instant.now())).isFalse();
    }

    @Test
    void activeBannerShown() {
        Banner b = new Banner("k", "/api/media/x");
        b.setActive(true);
        assertThat(MerchService.withinWindow(b, Instant.now())).isTrue();
    }
}
