package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.image.service.ImageUrlResolver;

class ImageUrlResolverTest {

    private final ImageUrlResolver resolver = new ImageUrlResolver("https://images.snowthing.org/");

    @Test
    void convertsCloudFrontUrlToStorageKey() {
        assertThat(resolver.toStorageValue("https://images.snowthing.org/public/posts/sample.png"))
                .isEqualTo("public/posts/sample.png");
    }

    @Test
    void convertsStorageKeyToCloudFrontUrl() {
        assertThat(resolver.toPublicUrl("public/posts/sample.png"))
                .isEqualTo("https://images.snowthing.org/public/posts/sample.png");
    }

    @Test
    void keepsLegacyExternalUrlUnchanged() {
        String legacyUrl = "https://cdn.example.com/sample.png";

        assertThat(resolver.toStorageValue(legacyUrl)).isEqualTo(legacyUrl);
        assertThat(resolver.toPublicUrl(legacyUrl)).isEqualTo(legacyUrl);
    }
}
