package com.ikae.snowthing.domain.image.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class PostImageLifecycleService {
    private final JdbcTemplate jdbc;
    private final ImageService images;
    private final ImageUrlResolver urls;
    private final S3Client s3;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucket;

    private static final String PREFIX = "public/posts/originals/";

    public PostImageLifecycleService(
            JdbcTemplate jdbc,
            ImageService images,
            ImageUrlResolver urls,
            S3Client s3,
            Clock clock,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.images = images;
        this.urls = urls;
        this.s3 = s3;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImageUploadResponse upload(MultipartFile file, String owner) {
        return upload(images.preparePostImage(file), owner);
    }

    public ImageUploadResponse upload(ImageService.PreparedPostImage prepared, String owner) {
        String key = prepared.key();
        tx.executeWithoutResult(
                status ->
                        jdbc.update(
                                "INSERT INTO post_image_upload(image_key,owner_email,state,due_at) VALUES(?,?,'PENDING',?)",
                                key,
                                owner,
                                Timestamp.from(clock.instant().plus(Duration.ofHours(1)))));
        return images.uploadPreparedImage(prepared);
    }

    // Must participate in the post transaction: cleanup and attachment share the same row lock.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void protect(List<String> references, String owner) {
        protect(references, owner, java.util.Set.of());
    }

    private void protect(List<String> references, String owner, java.util.Set<String> trusted) {
        if (references == null) return;
        for (String key :
                references.stream()
                        .map(urls::toStorageValue)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList()) {
            if (key.contains("%")) {
                try {
                    if (java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8)
                            .contains("public/posts/"))
                        throw new CustomException(ErrorCode.INVALID_INPUT);
                } catch (IllegalArgumentException invalidEncoding) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
            }
            if (key.contains(PREFIX + "m-") && !key.startsWith(PREFIX + "m-"))
                throw new CustomException(ErrorCode.INVALID_INPUT);
            if (!key.startsWith("public/posts/")) continue;
            var rows =
                    jdbc.query(
                            "SELECT owner_email,state FROM post_image_upload WHERE image_key=? FOR UPDATE",
                            (row, index) -> new String[] {row.getString(1), row.getString(2)},
                            key);
            boolean managed = key.startsWith(PREFIX + "m-");
            if (rows.isEmpty() && !managed) continue;
            if (rows.isEmpty()
                    || (managed
                            && !trusted.contains(key)
                            && (owner == null || !owner.equals(rows.get(0)[0])))
                    || !"PENDING".equals(rows.get(0)[1]))
                throw new CustomException(ErrorCode.INVALID_INPUT);
            jdbc.update(
                    "UPDATE post_image_upload SET due_at=? WHERE image_key=?",
                    Timestamp.from(clock.instant().plus(Duration.ofHours(1))),
                    key);
        }
    }

    @Transactional
    public void renew(List<String> references, String owner) {
        protect(references, owner);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void protectAll(List<String> references, String content, String owner) {
        protect(List.copyOf(collectKeys(references, content)), owner);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void protectExisting(
            List<String> references,
            String content,
            String owner,
            List<String> previousReferences,
            String previousContent) {
        protect(
                List.copyOf(collectKeys(references, content)),
                owner,
                collectKeys(previousReferences, previousContent));
    }

    private java.util.Set<String> collectKeys(List<String> references, String content) {
        var matcher =
                java.util.regex.Pattern.compile(
                                "public/posts/(originals/)?(m-)?[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)")
                        .matcher(content == null ? "" : content);
        var keys =
                new java.util.LinkedHashSet<String>(
                        references == null ? List.of() : List.copyOf(references));
        while (matcher.find()) {
            keys.add(matcher.group());
            if (keys.size() > 30) throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return keys.stream()
                .map(urls::toStorageValue)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Scheduled(fixedDelayString = "${snowthing.post-images.cleanup-delay-ms:60000}")
    public void cleanup() {
        jdbc.update(
                "DELETE FROM post_image_upload WHERE state='DELETED' AND image_key LIKE 'public/posts/originals/m-%' AND due_at<=? LIMIT 100",
                Timestamp.from(clock.instant()));
        var keys =
                jdbc.queryForList(
                        "SELECT image_key FROM post_image_upload WHERE state<>'DELETED' AND due_at<=? ORDER BY due_at LIMIT 50",
                        String.class,
                        Timestamp.from(clock.instant()));
        for (String key : keys) {
            try {
                cleanOne(key);
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn(
                                "Post image cleanup deferred: {}",
                                failure.getClass().getSimpleName());
            }
        }
    }

    public void cleanOne(String key) {
        if (!key.matches("public/posts/(originals/)?(m-)?[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)"))
            return;
        boolean claimed =
                Boolean.TRUE.equals(
                        tx.execute(
                                status -> {
                                    var rows =
                                            jdbc.query(
                                                    "SELECT due_at FROM post_image_upload WHERE image_key=? FOR UPDATE",
                                                    (row, index) -> row.getTimestamp(1).toInstant(),
                                                    key);
                                    if (rows.isEmpty() || rows.get(0).isAfter(clock.instant()))
                                        return false;
                                    // Includes soft-deleted posts: recovery must retain their
                                    // photos.
                                    String filename = key.substring(key.lastIndexOf('/') + 1);
                                    String stem = filename.substring(0, filename.lastIndexOf('.'));
                                    Long count =
                                            jdbc.queryForObject(
                                                    "SELECT COUNT(*) FROM post_image WHERE image_url LIKE ?",
                                                    Long.class,
                                                    "%" + stem + "%");
                                    Long inline =
                                            jdbc.queryForObject(
                                                    "SELECT COUNT(*) FROM post WHERE content LIKE ?",
                                                    Long.class,
                                                    "%" + stem + "%");
                                    if ((count != null && count > 0)
                                            || (inline != null && inline > 0)) {
                                        jdbc.update(
                                                "UPDATE post_image_upload SET due_at=? WHERE image_key=?",
                                                Timestamp.from(
                                                        clock.instant().plus(Duration.ofHours(1))),
                                                key);
                                        return false;
                                    }
                                    jdbc.update(
                                            "UPDATE post_image_upload SET state='DELETING',due_at=? WHERE image_key=?",
                                            Timestamp.from(
                                                    clock.instant().plus(Duration.ofMinutes(10))),
                                            key);
                                    return true;
                                }));
        if (!claimed) return;
        String filename = key.substring(key.lastIndexOf('/') + 1);
        String thumb =
                "public/posts/thumbnails/"
                        + filename.substring(0, filename.lastIndexOf('.'))
                        + ".jpg";
        for (String object : key.startsWith(PREFIX + "m-") ? List.of(key) : List.of(key, thumb)) {
            try {
                s3.putObjectTagging(
                        software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest.builder()
                                .bucket(bucket)
                                .key(object)
                                .tagging(
                                        software.amazon.awssdk.services.s3.model.Tagging.builder()
                                                .tagSet(
                                                        software.amazon.awssdk.services.s3.model.Tag
                                                                .builder()
                                                                .key("snowthing-cleanup")
                                                                .value("ready")
                                                                .build())
                                                .build())
                                .build());
            } catch (software.amazon.awssdk.services.s3.model.S3Exception failure) {
                if (failure.statusCode() != 404) throw failure;
            }
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object).build());
        }
        // Tombstones reject stale attempts to attach an already deleted upload.
        tx.executeWithoutResult(
                status ->
                        jdbc.update(
                                "UPDATE post_image_upload SET state='DELETED',due_at=? WHERE image_key=?",
                                Timestamp.from(clock.instant().plus(Duration.ofDays(7))),
                                key));
    }
}
