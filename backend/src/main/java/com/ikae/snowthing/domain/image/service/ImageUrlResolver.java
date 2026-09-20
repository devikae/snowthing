package com.ikae.snowthing.domain.image.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageUrlResolver {

    private static final String ORIGINAL_PREFIX = "public/posts/originals/";
    private static final String THUMBNAIL_PREFIX = "public/posts/thumbnails/";

    private final String cloudFrontBaseUrl;

    public ImageUrlResolver(
            @Value("${cloud.aws.cloudfront.base-url:https://images.snowthing.org}")
                    String cloudFrontBaseUrl) {
        this.cloudFrontBaseUrl = cloudFrontBaseUrl.replaceAll("/+$", "");
    }

    public String toStorageValue(String imageReference) {
        if (imageReference == null) {
            return null;
        }

        String cloudFrontPrefix = cloudFrontBaseUrl + "/";
        if (imageReference.startsWith(cloudFrontPrefix)) {
            return imageReference.substring(cloudFrontPrefix.length());
        }
        return imageReference;
    }

    public String toPublicUrl(String storedValue) {
        if (storedValue == null
                || storedValue.startsWith("http://")
                || storedValue.startsWith("https://")) {
            return storedValue;
        }
        return cloudFrontBaseUrl + "/" + storedValue.replaceFirst("^/+", "");
    }

    public String toThumbnailPublicUrl(String storedValue) {
        if (storedValue == null
                || storedValue.startsWith("http://")
                || storedValue.startsWith("https://")
                || !storedValue.startsWith(ORIGINAL_PREFIX)) {
            return toPublicUrl(storedValue);
        }

        String filename = storedValue.substring(ORIGINAL_PREFIX.length());
        int extensionIndex = filename.lastIndexOf('.');
        String imageId = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        return toPublicUrl(THUMBNAIL_PREFIX + imageId + ".jpg");
    }
}
