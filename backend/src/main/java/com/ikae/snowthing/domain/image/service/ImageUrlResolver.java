package com.ikae.snowthing.domain.image.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageUrlResolver {

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
}
