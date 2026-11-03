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
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.CRC32;
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
        imageService =
                new ImageService(
                        s3Client,
                        org.mockito.Mockito.mock(
                                software.amazon.awssdk.services.cloudfront.CloudFrontClient.class));
        ReflectionTestUtils.setField(imageService, "bucketName", "snowthing-media-00001");
        ReflectionTestUtils.setField(
                imageService, "cloudFrontBaseUrl", "https://images.snowthing.org");
    }

    @Test
    void managedImagesAreResizedAndEncodedAsJpeg() throws Exception {
        var file = new MockMultipartFile("file", "large.png", "image/png", createPng(3000, 1500));
        var result = imageService.uploadImage(file, "m-11111111-1111-1111-1111-111111111111");
        var requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodies = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, times(1)).putObject(requests.capture(), bodies.capture());
        assertThat(result.imageKey()).endsWith(".jpg");
        assertThat(requests.getAllValues())
                .allSatisfy(request -> assertThat(request.contentType()).isEqualTo("image/jpeg"));
        assertThat(requests.getValue().key()).contains("/originals/");
        try (var input = bodies.getAllValues().get(0).contentStreamProvider().newStream()) {
            var image = ImageIO.read(input);
            assertThat(image.getWidth()).isLessThanOrEqualTo(1920);
            assertThat(image.getHeight()).isLessThanOrEqualTo(1920);
        }
    }

    @Test
    void marketThumbnailWritesOnlyOneSmallObject() throws Exception {
        when(s3Client.getObjectAsBytes(
                        any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenReturn(
                        software.amazon.awssdk.core.ResponseBytes.fromByteArray(
                                software.amazon.awssdk.services.s3.model.GetObjectResponse.builder()
                                        .build(),
                                createPng(1000, 600)));
        imageService.createMarketThumbnail(
                "public/posts/originals/m-11111111-1111-1111-1111-111111111111.jpg",
                "public/posts/thumbnails/market-22222222-2222-2222-2222-222222222222.jpg");
        var requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodies = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, times(1)).putObject(requests.capture(), bodies.capture());
        assertThat(requests.getValue().key()).contains("/thumbnails/market-");
        try (var input = bodies.getValue().contentStreamProvider().newStream()) {
            var image = ImageIO.read(input);
            assertThat(image.getWidth()).isLessThanOrEqualTo(320);
            assertThat(image.getHeight()).isLessThanOrEqualTo(320);
        }
    }

    @Test
    void exifRotationIsAppliedToManagedPhotosAndProfileThumbnailsWithoutUpscaling()
            throws Exception {
        // APP1 Exif / little-endian TIFF with orientation=6 (90 degrees clockwise).
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        byte[] app1 =
                java.util.HexFormat.of()
                        .parseHex(
                                "ffe1002245786966000049492a0008000000010012010300010000000600000000000000");
        var oriented = new ByteArrayOutputStream();
        byte[] original = jpeg.toByteArray();
        oriented.write(original, 0, 2);
        oriented.write(app1);
        oriented.write(original, 2, original.length - 2);
        var file =
                new MockMultipartFile("file", "portrait.jpg", "image/jpeg", oriented.toByteArray());
        var managed = imageService.preparePostImage(file);
        var profile = imageService.prepareProfileImage(file);
        for (byte[] bytes : List.of(managed.bytes(), profile.normalizedBytes())) {
            var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertThat(image.getWidth()).isEqualTo(60);
            assertThat(image.getHeight()).isEqualTo(100);
        }
        verifyNoInteractions(s3Client);
    }

    @Test
    void smallManagedPhotoIsNotUpscaled() throws Exception {
        imageService.uploadImage(
                new MockMultipartFile("file", "small.png", "image/png", createPng(100, 60)),
                "m-11111111-1111-1111-1111-111111111111");
        var bodies = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(any(PutObjectRequest.class), bodies.capture());
        try (var input = bodies.getValue().contentStreamProvider().newStream()) {
            var image = ImageIO.read(input);
            assertThat(image.getWidth()).isEqualTo(100);
            assertThat(image.getHeight()).isEqualTo(60);
        }
    }

    @Test
    void rejectsOversizedDimensionsBeforeDecodingOrWritingS3() {
        for (int[] size :
                new int[][] {{10001, 1}, {5001, 5000}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
            byte[] header = pngBytes.clone();
            ByteBuffer.wrap(header).putInt(16, size[0]).putInt(20, size[1]);
            CRC32 crc = new CRC32();
            crc.update(header, 12, 17);
            ByteBuffer.wrap(header).putInt(29, (int) crc.getValue());
            var file = new MockMultipartFile("file", "large.png", "image/png", header);
            assertThatThrownBy(() -> imageService.uploadImage(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
            assertThatThrownBy(() -> imageService.uploadProfileImage(file))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
        }
        verifyNoInteractions(s3Client);
    }

    @Test
    void rejectsDeclaredMimeThatDoesNotMatchActualFile() {
        var file = new MockMultipartFile("file", "picture.png", "image/jpeg", pngBytes);
        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_FILE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    void profileUploadNormalizesPixelsAndStoresOnlyOneJpeg() throws IOException {
        MockMultipartFile file =
                new MockMultipartFile("file", "portrait.png", "image/png", pngBytes);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        var result = imageService.uploadProfileImage(file);
        assertThat(result.imageKey()).startsWith("public/profiles/").endsWith(".jpg");
        var request = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            BufferedImage image = ImageIO.read(input);
            assertThat(image.getWidth()).isLessThanOrEqualTo(320);
            assertThat(image.getHeight()).isLessThanOrEqualTo(320);
        }
    }

    @Test
    void profileUploadRejectsSignatureOnlyFakeImageWithoutWritingS3() {
        byte[] fake = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        var file = new MockMultipartFile("file", "fake.png", "image/png", fake);
        assertThatThrownBy(() -> imageService.uploadProfileImage(file))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(s3Client);
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
