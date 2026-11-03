package com.ikae.snowthing.domain.image.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ikae.snowthing.domain.image.dto.ImageUploadResponse;
import com.ikae.snowthing.domain.image.entity.PostImageUpload;
import com.ikae.snowthing.domain.image.repository.PostImageUploadRepository;
import com.ikae.snowthing.domain.post.repository.PostImageRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Service
public class PostImageLifecycleService {
    private final PostImageUploadRepository postImageUploadRepository;
    private final PostImageRepository postImageRepository;
    private final ImageService images;
    private final ImageUrlResolver urls;
    private final S3Client s3;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucket;

    private static final String PREFIX = "public/posts/originals/";

    public PostImageLifecycleService(
            PostImageUploadRepository postImageUploadRepository,
            PostImageRepository postImageRepository,
            ImageService images,
            ImageUrlResolver urls,
            S3Client s3,
            Clock clock,
            PlatformTransactionManager manager) {
        this.postImageUploadRepository = postImageUploadRepository;
        this.postImageRepository = postImageRepository;
        this.images = images;
        this.urls = urls;
        this.s3 = s3;
        this.clock = clock;
        this.tx = new TransactionTemplate(manager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ImageUploadResponse upload(MultipartFile file, String owner) {
        return upload(images.preparePostImage(file), owner);
    }

    public ImageUploadResponse upload(ImageService.PreparedPostImage prepared, String owner) {
        String key = prepared.key();
        tx.executeWithoutResult(
                status ->
                        postImageUploadRepository.save(
                                PostImageUpload.builder()
                                        .key(key)
                                        .owner(owner)
                                        .state("PENDING")
                                        .due(clock.instant().plus(Duration.ofHours(1)))
                                        .build()));
        return images.uploadPreparedImage(prepared);
    }

    // Must participate in the post transaction: cleanup and attachment share the same row lock.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void protect(List<String> references, String owner) {
        protect(references, owner, Set.of());
    }

    private void protect(List<String> references, String owner, Set<String> trusted) {
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

            var uploadOpt = postImageUploadRepository.findByKeyForUpdate(key);
            boolean managed = key.startsWith(PREFIX + "m-");
            if (uploadOpt.isEmpty() && !managed) continue;
            if (uploadOpt.isEmpty()
                    || (managed
                            && !trusted.contains(key)
                            && (owner == null || !owner.equals(uploadOpt.get().getOwner())))
                    || !"PENDING".equals(uploadOpt.get().getState()))
                throw new CustomException(ErrorCode.INVALID_INPUT);

            uploadOpt.get().renewDue(clock.instant().plus(Duration.ofHours(1)));
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

    public Set<String> collectKeys(List<String> references, String content) {
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
        tx.executeWithoutResult(
                status -> postImageUploadRepository.deleteCompletedPurges(clock.instant()));
        var keys = postImageUploadRepository.findKeysForCleanup(clock.instant());
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
                                    var uploadOpt =
                                            postImageUploadRepository.findByKeyForUpdate(key);
                                    if (uploadOpt.isEmpty()) return false;
                                    var upload = uploadOpt.get();
                                    if (upload.getDue().isAfter(clock.instant())) return false;

                                    // Includes soft-deleted posts: recovery must retain their
                                    // photos.
                                    // Both gallery and inline references use indexed lookups.
                                    boolean inUse =
                                            postImageRepository.existsByImageUrlIn(
                                                            List.of(key, urls.toPublicUrl(key)))
                                                    || postImageRepository.countContentReference(
                                                                    key)
                                                            > 0;
                                    if (inUse) {
                                        upload.renewDue(clock.instant().plus(Duration.ofHours(1)));
                                        return false;
                                    }
                                    upload.markDeleting(
                                            clock.instant().plus(Duration.ofMinutes(10)));
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
                        postImageUploadRepository
                                .findByKeyForUpdate(key)
                                .ifPresent(
                                        upload -> {
                                            upload.markDeleted();
                                            upload.renewDue(
                                                    clock.instant().plus(Duration.ofDays(7)));
                                        }));
    }
}
