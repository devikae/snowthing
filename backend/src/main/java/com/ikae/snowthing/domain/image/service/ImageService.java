package com.ikae.snowthing.domain.image.service;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageDownloadResponse;
import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucketName;

    public ImageUploadResponse uploadImage(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        String imageKey = "images/" + UUID.randomUUID() + "." + extension;

        try {
            PutObjectRequest putRequest =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(imageKey)
                            .contentType(file.getContentType())
                            .build();

            s3Client.putObject(
                    putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            String imageUrl = "/api/v1/images/" + imageKey;
            log.info("S3 이미지 업로드 성공: bucket={}, key={}", bucketName, imageKey);
            return new ImageUploadResponse(imageUrl, imageKey);
        } catch (IOException | S3Exception e) {
            log.error(
                    "S3 이미지 업로드 실패: bucket={}, key={}, error={}",
                    bucketName,
                    imageKey,
                    e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public ImageDownloadResponse getImage(String imageKey) {
        if (imageKey == null || imageKey.isBlank() || imageKey.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        try {
            GetObjectRequest getRequest =
                    GetObjectRequest.builder().bucket(bucketName).key(imageKey).build();

            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            byte[] bytes = s3Object.readAllBytes();
            String contentType = s3Object.response().contentType();

            return new ImageDownloadResponse(bytes, contentType);
        } catch (NoSuchKeyException e) {
            log.warn("S3 이미지를 찾을 수 없음: bucket={}, key={}", bucketName, imageKey);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        } catch (IOException | S3Exception e) {
            log.error(
                    "S3 이미지 조회 실패: bucket={}, key={}, error={}",
                    bucketName,
                    imageKey,
                    e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
