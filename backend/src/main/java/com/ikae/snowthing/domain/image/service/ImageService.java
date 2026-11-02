package com.ikae.snowthing.domain.image.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

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
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final int MAX_IMAGE_SIDE = 10_000;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;
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
    private final CloudFrontClient cloudFrontClient;

    @Value("${cloud.aws.cloudfront.distribution-id:}")
    private String distributionId;

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

    public ImageUploadResponse uploadProfileImage(MultipartFile file) {
        return uploadProfileImage(file, "public/profiles/" + UUID.randomUUID() + ".jpg");
    }

    public ImageUploadResponse uploadProfileImage(MultipartFile file, String key) {
        validateProfileKey(key);
        byte[] bytes = readImageBytes(file);
        validateFile(file, bytes);
        byte[] normalized = createThumbnail(bytes);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType("image/jpeg")
                            .cacheControl("no-store")
                            .build(),
                    RequestBody.fromBytes(normalized));
            return new ImageUploadResponse(
                    cloudFrontBaseUrl.replaceAll("/+$", "") + "/" + key, key);
        } catch (SdkException exception) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    public String profileUrl(String key) {
        validateProfileKey(key);
        return cloudFrontBaseUrl.replaceAll("/+$", "") + "/" + key;
    }

    public Optional<String> profileKey(String url) {
        String prefix = cloudFrontBaseUrl.replaceAll("/+$", "") + "/";
        if (url == null || !url.startsWith(prefix)) return Optional.empty();
        String key = url.substring(prefix.length());
        return key.matches("public/profiles/[0-9a-fA-F-]{36}\\.jpg")
                ? Optional.of(key)
                : Optional.empty();
    }

    private void validateProfileKey(String key) {
        if (key == null || !key.matches("public/profiles/[0-9a-fA-F-]{36}\\.jpg"))
            throw new com.ikae.snowthing.global.exception.CustomException(ErrorCode.INVALID_INPUT);
    }

    public void deleteProfileImage(String key) {
        validateProfileKey(key);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        if (distributionId == null || distributionId.isBlank())
            throw new com.ikae.snowthing.global.exception.CustomException(
                    ErrorCode.PROFILE_CDN_NOT_CONFIGURED);
        cloudFrontClient.createInvalidation(
                CreateInvalidationRequest.builder()
                        .distributionId(distributionId)
                        .invalidationBatch(
                                InvalidationBatch.builder()
                                        .callerReference(
                                                "profile-delete-"
                                                        + key.substring(key.lastIndexOf('/') + 1))
                                        .paths(Paths.builder().quantity(1).items("/" + key).build())
                                        .build())
                        .build());
    }

    public byte[] normalizeCrewImage(MultipartFile file, int width, int height) {
        try {
            byte[] bytes = readImageBytes(file);
            validateFile(file, bytes);
            try (var input = new ByteArrayInputStream(bytes);
                    var output = new ByteArrayOutputStream()) {
                Thumbnails.of(input)
                        .size(width, height)
                        .outputFormat("jpg")
                        .outputQuality(0.85)
                        .toOutputStream(output);
                return output.toByteArray();
            }
        } catch (BusinessException exception) {
            throw new com.ikae.snowthing.global.exception.CustomException(
                    exception.getErrorCode(), exception);
        } catch (IOException | RuntimeException exception) {
            throw new com.ikae.snowthing.global.exception.CustomException(
                    ErrorCode.INVALID_FILE_TYPE, exception);
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
        String expectedMime =
                switch (extension) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    default -> "image/webp";
                };
        if (contentType == null
                || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))
                || !expectedMime.equalsIgnoreCase(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (!hasValidSignature(extension, imageBytes)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
        validateImageDimensions(imageBytes, extension);
    }

    private void validateImageDimensions(byte[] bytes, String extension) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String expectedFormat = extension.equals("jpg") ? "jpeg" : extension;
                if (!expectedFormat.equalsIgnoreCase(reader.getFormatName())) {
                    throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0)
                    throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
                if (width > MAX_IMAGE_SIDE
                        || height > MAX_IMAGE_SIDE
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new BusinessException(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
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
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
}
