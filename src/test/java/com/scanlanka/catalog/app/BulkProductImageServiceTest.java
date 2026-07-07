package com.scanlanka.catalog.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure filename-convention + size-normalisation logic (DB-free); the fuzzy matcher is the risky part. */
class BulkProductImageServiceTest {

    @Test
    void basenameStripsAnyZipPath() {
        assertThat(BulkProductImageService.basename("photos/boards/scan-white-board__4x3.jpg"))
            .isEqualTo("scan-white-board__4x3.jpg");
        assertThat(BulkProductImageService.basename("..\\..\\evil\\x.png")).isEqualTo("x.png");
        assertThat(BulkProductImageService.basename("plain.png")).isEqualTo("plain.png");
    }

    @Test
    void stripExtensionKeepsDoubleUnderscoreTokens() {
        assertThat(BulkProductImageService.stripExtension("scan-white-board__4x3.jpg"))
            .isEqualTo("scan-white-board__4x3");
        assertThat(BulkProductImageService.stripExtension("no-ext")).isEqualTo("no-ext");
    }

    @Test
    void fractionsAndSpacesNormaliseToTheSameCanonicalSize() {
        // the exact case that matters for boards: sheet label vs a filename-friendly token
        assertThat(BulkProductImageService.normSize("1 1/2 x 1 1/2"))
            .isEqualTo(BulkProductImageService.normSize("1.5x1.5"));
        assertThat(BulkProductImageService.normSize("4 x 3")).isEqualTo(BulkProductImageService.normSize("4x3"));
        assertThat(BulkProductImageService.normSize("2.5 x 1 1/2"))
            .isEqualTo(BulkProductImageService.normSize("2.5x1.5"));
    }

    @Test
    void normaliseHandlesMultiplySignFeetAndTrailingZero() {
        assertThat(BulkProductImageService.normSize("6 × 4 ft")).isEqualTo("6x4");
        assertThat(BulkProductImageService.normSize("2.0 x 2.0")).isEqualTo("2x2");
        assertThat(BulkProductImageService.normSize("  8X4  ")).isEqualTo("8x4");
    }

    @Test
    void differentSizesDoNotCollide() {
        assertThat(BulkProductImageService.normSize("4x3")).isNotEqualTo(BulkProductImageService.normSize("3x4"));
        assertThat(BulkProductImageService.normSize("1 1/2 x 1 1/2"))
            .isNotEqualTo(BulkProductImageService.normSize("1x1"));
    }
}
