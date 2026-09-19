package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.BusinessException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private byte[] pngBytes;

    @Mock private S3Client s3Client;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        pngBytes = createPng(800, 600);
        imageService = new ImageService(s3Client);
        ReflectionTestUtils.setField(imageService, "bucketName", "snowthing-media-00001");
        ReflectionTestUtils.setField(
                imageService, "cloudFrontBaseUrl", "https://images.snowthing.org");
    }

    @Test
    @DisplayName("이미지를 S3 공개 게시글 경로에 저장하고 CloudFront URL을 반환한다")
    void uploadImage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ImageUploadResponse response = imageService.uploadImage(file);

        assertThat(response.imageKey()).startsWith("public/posts/originals/").endsWith(".png");
        assertThat(response.imageUrl())
                .isEqualTo("https://images.snowthing.org/" + response.imageKey());

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(2)).putObject(requestCaptor.capture(), any(RequestBody.class));
        List<PutObjectRequest> requests = requestCaptor.getAllValues();
        assertThat(requests)
                .extracting(PutObjectRequest::key)
                .anyMatch(key -> key.startsWith("public/posts/thumbnails/") && key.endsWith(".jpg"))
                .anyMatch(key -> key.equals(response.imageKey()));
        assertThat(requests)
                .extracting(PutObjectRequest::cacheControl)
                .containsOnly("public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("5MB를 초과한 이미지는 업로드하지 않는다")
    void uploadImage_exceedsMaxSize() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("허용하지 않은 확장자의 파일은 업로드하지 않는다")
    void uploadImage_invalidExtension() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "danger.exe", "application/octet-stream", "malicious".getBytes());

        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("확장자와 MIME이 이미지여도 실제 파일 시그니처가 다르면 업로드하지 않는다")
    void uploadImage_invalidSignature() {
        MockMultipartFile file =
                new MockMultipartFile("file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("S3 SDK 업로드 실패를 파일 업로드 오류로 변환한다")
    void uploadImage_sdkFailure_throwsFileUploadFailed() {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngBytes);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.builder().message("network failure").build());

        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_UPLOAD_FAILED);
    }

    private byte[] createPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
