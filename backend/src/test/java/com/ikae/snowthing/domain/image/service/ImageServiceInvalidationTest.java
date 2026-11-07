package com.ikae.snowthing.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.s3.S3Client;

class ImageServiceInvalidationTest {

    private final CloudFrontClient cloudFront = mock(CloudFrontClient.class);
    private final ImageService service = new ImageService(mock(S3Client.class), cloudFront);

    @Test
    @DisplayName("지운 사진의 원본과 썸네일 경로를 무효화한다")
    void invalidatesPaths() {
        ReflectionTestUtils.setField(service, "distributionId", "DIST");

        service.invalidateDeletedPostImages(List.of("public/posts/a.jpg", "public/posts/b.jpg"));

        ArgumentCaptor<CreateInvalidationRequest> request =
                ArgumentCaptor.forClass(CreateInvalidationRequest.class);
        verify(cloudFront).createInvalidation(request.capture());
        assertThat(request.getValue().distributionId()).isEqualTo("DIST");
        assertThat(request.getValue().invalidationBatch().paths().items())
                .containsExactly("/public/posts/a.jpg", "/public/posts/b.jpg");
    }

    @Test
    @DisplayName("배포 ID가 없으면 호출하지 않는다")
    void skipsWithoutDistribution() {
        ReflectionTestUtils.setField(service, "distributionId", "");

        service.invalidateDeletedPostImages(List.of("public/posts/a.jpg"));

        verify(cloudFront, never()).createInvalidation(any(CreateInvalidationRequest.class));
    }

    @Test
    @DisplayName("무효화가 실패해도 예외를 던지지 않는다")
    void failureDoesNotPropagate() {
        ReflectionTestUtils.setField(service, "distributionId", "DIST");
        when(cloudFront.createInvalidation(any(CreateInvalidationRequest.class)))
                .thenThrow(new IllegalStateException("denied"));

        assertThatCode(() -> service.invalidateDeletedPostImages(List.of("public/posts/a.jpg")))
                .doesNotThrowAnyException();
    }
}
