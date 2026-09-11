package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.image.dto.ImageDownloadResponse;
import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.service.ImageService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.BusinessException;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @Mock private S3Client s3Client;

    private ImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new ImageService(s3Client);
        ReflectionTestUtils.setField(imageService, "bucketName", "snowthing-media-00001");
    }

    @Test
    @DisplayName("정상 이미지 업로드 시 S3 putObject 호출 후 ImageUploadResponse 반환")
    void uploadImage_success() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "test.png", "image/png", "test-image-content".getBytes());

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        ImageUploadResponse response = imageService.uploadImage(file);

        assertThat(response.imageUrl()).startsWith("/api/v1/images/images/");
        assertThat(response.imageUrl()).endsWith(".png");
        assertThat(response.imageKey()).startsWith("images/");
        assertThat(response.imageKey()).endsWith(".png");

        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("5MB 초과 파일 업로드 시 FILE_SIZE_EXCEEDED 예외 발생")
    void uploadImage_exceedsMaxSize() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file =
                new MockMultipartFile("file", "large.png", "image/png", largeBytes);

        assertThatThrownBy(() -> imageService.uploadImage(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("지원하지 않는 확장자 파일 업로드 시 INVALID_FILE_TYPE 예외 발생")
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
    @DisplayName("정상 이미지 조회 시 ResponseInputStream에서 바이트 배열과 contentType 반환")
    void getImage_success() {
        String key = "images/sample.png";
        byte[] expectedBytes = "image-bytes".getBytes();
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(
                        response,
                        AbortableInputStream.create(new ByteArrayInputStream(expectedBytes)));

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        ImageDownloadResponse download = imageService.getImage(key);

        assertThat(download.data()).isEqualTo(expectedBytes);
        assertThat(download.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("경로 탐색(..) 등 악의적인 key 요청 시 INVALID_INPUT 예외 발생")
    void getImage_pathTraversal_throwsException() {
        assertThatThrownBy(() -> imageService.getImage("../../../etc/passwd"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("존재하지 않는 파일 키 조회 시 FILE_NOT_FOUND 예외 발생")
    void getImage_notFound_throwsException() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(
                        NoSuchKeyException.builder()
                                .message("The specified key does not exist.")
                                .build());

        assertThatThrownBy(() -> imageService.getImage("images/nonexistent.png"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }
}
