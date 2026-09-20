package com.ikae.snowthing.domain.image.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final String ORIGINAL_PREFIX = "public/posts/originals/";
    private static final String THUMBNAIL_PREFIX = "public/posts/thumbnails/";
    private static final int THUMBNAIL_MAX_WIDTH = 320;
    private static final int THUMBNAIL_MAX_HEIGHT = 320;
    private static final double THUMBNAIL_QUALITY = 0.8;
    private static final String IMMUTABLE_CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucketName;

    @Value("${cloud.aws.cloudfront.base-url:https://images.snowthing.org}")
    private String cloudFrontBaseUrl;

    public ImageUploadResponse uploadImage(MultipartFile file) {
        byte[] imageBytes = readImageBytes(file);
        validateFile(file, imageBytes);

        String extension = extractExtension(file.getOriginalFilename());
        String imageId = UUID.randomUUID().toString();
        String imageKey = ORIGINAL_PREFIX + imageId + "." + extension;
        String thumbnailKey = THUMBNAIL_PREFIX + imageId + ".jpg";
        byte[] thumbnailBytes = createThumbnail(imageBytes);

        try {
            putObject(thumbnailKey, "image/jpeg", thumbnailBytes);
            putObject(imageKey, file.getContentType(), imageBytes);

            String imageUrl = cloudFrontBaseUrl.replaceAll("/+$", "") + "/" + imageKey;
            log.info("S3 이미지 업로드 성공: bucket={}, key={}", bucketName, imageKey);
            return new ImageUploadResponse(imageUrl, imageKey);
        } catch (SdkException e) {
            log.error(
                    "S3 이미지 업로드 실패: bucket={}, key={}, error={}",
                    bucketName,
                    imageKey,
                    e.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void putObject(String key, String contentType, byte[] bytes) {
        PutObjectRequest putRequest =
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl(IMMUTABLE_CACHE_CONTROL)
                        .build();
        s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
    }

    private byte[] createThumbnail(byte[] imageBytes) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(imageBytes);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Thumbnails.of(input)
                    .size(THUMBNAIL_MAX_WIDTH, THUMBNAIL_MAX_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(THUMBNAIL_QUALITY)
                    .toOutputStream(output);
            return output.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.warn("썸네일 생성 실패: error={}", e.getMessage());
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private byte[] readImageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void validateFile(MultipartFile file, byte[] imageBytes) {

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (!hasValidSignature(extension, imageBytes)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private boolean hasValidSignature(String extension, byte[] bytes) {
        return switch (extension) {
            case "jpg", "jpeg" ->
                    bytes.length >= 3
                            && unsigned(bytes[0]) == 0xFF
                            && unsigned(bytes[1]) == 0xD8
                            && unsigned(bytes[2]) == 0xFF;
            case "png" ->
                    bytes.length >= 8
                            && unsigned(bytes[0]) == 0x89
                            && bytes[1] == 'P'
                            && bytes[2] == 'N'
                            && bytes[3] == 'G'
                            && unsigned(bytes[4]) == 0x0D
                            && unsigned(bytes[5]) == 0x0A
                            && unsigned(bytes[6]) == 0x1A
                            && unsigned(bytes[7]) == 0x0A;
            case "webp" ->
                    bytes.length >= 12
                            && bytes[0] == 'R'
                            && bytes[1] == 'I'
                            && bytes[2] == 'F'
                            && bytes[3] == 'F'
                            && bytes[8] == 'W'
                            && bytes[9] == 'E'
                            && bytes[10] == 'B'
                            && bytes[11] == 'P';
            default -> false;
        };
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
