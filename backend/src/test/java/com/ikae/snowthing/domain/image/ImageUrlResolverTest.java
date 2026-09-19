package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.image.service.ImageUrlResolver;

class ImageUrlResolverTest {

    private final ImageUrlResolver resolver = new ImageUrlResolver("https://images.snowthing.org/");

    @Test
    void resolvesNewOriginalKeyToThumbnailUrl() {
        String result = resolver.toThumbnailPublicUrl("public/posts/originals/image-id.png");

        assertThat(result)
                .isEqualTo("https://images.snowthing.org/public/posts/thumbnails/image-id.jpg");
    }

    @Test
    void resolvesLegacyKeyToOriginalUrl() {
        String result = resolver.toThumbnailPublicUrl("public/posts/legacy-image.jpg");

        assertThat(result).isEqualTo("https://images.snowthing.org/public/posts/legacy-image.jpg");
    }

    @Test
    void preservesLegacyAbsoluteUrl() {
        String result = resolver.toThumbnailPublicUrl("https://legacy.example.com/image.jpg");

        assertThat(result).isEqualTo("https://legacy.example.com/image.jpg");
    }
}
