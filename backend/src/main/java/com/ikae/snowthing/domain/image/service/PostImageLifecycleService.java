package com.ikae.snowthing.domain.image.service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostImage;
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
    private final MarketThumbnailService marketThumbnails;
    private final S3Client s3;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Value("${cloud.aws.s3.bucket:snowthing-media-00001}")
    private String bucket;

    private static final String PREFIX = "public/posts/originals/";
    private static final java.util.regex.Pattern CONTENT_IMAGE_KEY =
            java.util.regex.Pattern.compile(
                    "public/posts/(originals/)?(m-)?[0-9a-f-]{36}\\.(jpg|jpeg|png|webp)");
    private static final java.util.regex.Pattern INLINE_IMAGE =
            java.util.regex.Pattern.compile(
                    "data:image/[a-z0-9.+-]+;base64,", java.util.regex.Pattern.CASE_INSENSITIVE);

    public PostImageLifecycleService(
            PostImageUploadRepository postImageUploadRepository,
            PostImageRepository postImageRepository,
            ImageService images,
            ImageUrlResolver urls,
            MarketThumbnailService marketThumbnails,
            S3Client s3,
            Clock clock,
            PlatformTransactionManager manager) {
        this.postImageUploadRepository = postImageUploadRepository;
        this.postImageRepository = postImageRepository;
        this.images = images;
        this.urls = urls;
        this.marketThumbnails = marketThumbnails;
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

    /** 본문에 처음 나오는 사진 키. 사진 칸 사진이 없는 매물의 대표 사진으로 쓴다. 없으면 null. */
    public String firstContentKey(String content) {
        var matcher = CONTENT_IMAGE_KEY.matcher(content == null ? "" : content);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * base64 사진은 본문과 함께 DB에 쌓이므로 새로 넣지 못하게 막는다. 이미 base64 사진이 들어 있는 기존 글은 그대로 수정할 수 있도록 개수가 늘어날 때만
     * 거절한다.
     */
    public void rejectNewInlineImages(String content, String previousContent) {
        if (countInlineImages(content) > countInlineImages(previousContent)) {
            throw new CustomException(ErrorCode.INLINE_IMAGE_NOT_ALLOWED);
        }
    }

    private static long countInlineImages(String content) {
        return content == null ? 0 : INLINE_IMAGE.matcher(content).results().count();
    }

    public Set<String> collectKeys(List<String> references, String content) {
        var matcher = CONTENT_IMAGE_KEY.matcher(content == null ? "" : content);
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

    /**
     * 글이 더는 사진을 쓰지 않게 하고 사진을 다음 정리 작업의 대상으로 올린다. S3는 부르지 않는다. 글 변경과 같이 커밋되므로, 삭제가 롤백되면 정리 대상에도 오르지
     * 않는다.
     *
     * <p>참조를 지우는 이유: 정리 작업은 남아 있는 참조를 모두 사용 중으로 본다. 그 판단을 "지워지지 않은 글"로 바꾸면 이 코드를 배포하는 순간 이전에 삭제된 글의
     * 사진까지 한 시간 안에 모두 지워진다. 이전 글은 관리자가 수동 절차로 정리한다.
     *
     * @return 정리 대상에 올린 키와, 다른 글이 함께 써서 지우지 않는 키
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ReleasedImages releasePostImages(Post post) {
        Set<String> keys = new TreeSet<>(post.getContentImageKeys());
        for (PostImage image : post.getImages()) {
            keys.add(urls.toStorageValue(image.getImageUrl()));
        }
        // collectKeys는 작성 입력을 막으려고 30개가 넘으면 거절한다. 삭제는 막으면 안 되므로 쓰지 않는다.
        var matcher = CONTENT_IMAGE_KEY.matcher(post.getContent() == null ? "" : post.getContent());
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        keys.removeIf(key -> !CONTENT_IMAGE_KEY.matcher(key).matches());

        post.replaceImages(List.of());
        post.replaceContentImageKeys(Set.of());
        postImageUploadRepository.flush();

        var queued = new java.util.ArrayList<String>();
        var shared = new java.util.ArrayList<String>();
        for (String key : keys) {
            // 정리 작업은 참조 표만 본다. 표에 없는 옛 사진은 다른 글 본문에 있어도 찾지 못하므로 여기서 거른다.
            if (!postImageUploadRepository.existsById(key)
                    && postImageRepository.countOtherLivePostBody(key, post.getId()) > 0) {
                shared.add(key);
                continue;
            }
            postImageUploadRepository.insertIfAbsent(key);
            postImageUploadRepository.expireIfPending(key, clock.instant());
            (isReferenced(key) ? shared : queued).add(key);
        }
        marketThumbnails.release(post.getPublicId());
        return new ReleasedImages(List.copyOf(queued), List.copyOf(shared));
    }

    public record ReleasedImages(List<String> queuedKeys, List<String> sharedKeys) {}

    private boolean isReferenced(String key) {
        return postImageRepository.existsByImageUrlIn(List.of(key, urls.toPublicUrl(key)))
                || postImageRepository.countContentReference(key) > 0;
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

                                    // Hidden posts keep their references. Posts deleted from now
                                    // on have released theirs; older deleted posts stay protected
                                    // until the manual procedure in database/manual runs.
                                    // Both gallery and inline references use indexed lookups.
                                    if (isReferenced(key)) {
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
        List<String> objects = key.startsWith(PREFIX + "m-") ? List.of(key) : List.of(key, thumb);
        for (String object : objects) {
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
                // 역할에 ListBucket이 없어 없는 파일도 403이 온다. 진짜 권한 문제면 아래 삭제가 실패한다.
                if (failure.statusCode() != 404 && failure.statusCode() != 403) throw failure;
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
        images.invalidateDeletedPostImages(objects);
    }
}
