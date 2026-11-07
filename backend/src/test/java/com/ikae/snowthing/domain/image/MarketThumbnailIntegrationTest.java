package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.image.service.*;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@SpringBootTest(properties = "snowthing.market-thumbnails.delay-ms=36000000")
class MarketThumbnailIntegrationTest {
    @Autowired MarketThumbnailService thumbnails;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.ikae.snowthing.domain.post.repository.PostRepository posts;
    @Autowired com.ikae.snowthing.domain.post.repository.PostCategoryRepository categories;
    @Autowired com.ikae.snowthing.domain.market.service.MarketListingService listings;

    @Autowired
    com.ikae.snowthing.domain.market.repository.MarketListingRepository listingRepository;

    @Autowired
    com.ikae.snowthing.domain.market.repository.MarketCategoryRepository marketCategories;

    @Autowired com.ikae.snowthing.domain.member.repository.MemberRepository members;
    @MockitoBean ImageService images;
    @MockitoBean S3Client s3;
    @MockitoBean SharedRequestLimiter limits;
    private String id;
    private String first;
    private String second;

    @BeforeEach
    void setup() {
        var category =
                categories
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                com.ikae.snowthing.domain.post.entity.PostCategory
                                                        .builder()
                                                        .code("FREE")
                                                        .name("FREE")
                                                        .build()));
        id =
                posts.save(
                                com.ikae.snowthing.domain.post.entity.Post.builder()
                                        .category(category)
                                        .title("thumbnail")
                                        .content("body")
                                        .writerIp("127.0.0.1")
                                        .isAnonymous(true)
                                        .build())
                        .getPublicId();
        first = "public/posts/originals/m-" + UUID.randomUUID() + ".jpg";
        second = "public/posts/originals/m-" + UUID.randomUUID() + ".jpg";
        when(limits.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));
    }

    private record Seed(com.ikae.snowthing.domain.member.entity.Member member, String publicId) {
        com.ikae.snowthing.global.security.CustomUserDetails user() {
            return new com.ikae.snowthing.global.security.CustomUserDetails(member);
        }
    }

    private Seed seedListing(String content) {
        return new TransactionTemplate(manager)
                .execute(
                        status -> {
                            String unique = UUID.randomUUID().toString();
                            var member =
                                    members.save(
                                            com.ikae.snowthing.domain.member.entity.Member.builder()
                                                    .email(unique + "@test.invalid")
                                                    .nickname(unique)
                                                    .role(
                                                            com.ikae.snowthing.domain.member.entity
                                                                    .Role.ROLE_USER)
                                                    .build());
                            var postCategory =
                                    categories
                                            .findByCode("MARKET")
                                            .orElseGet(
                                                    () ->
                                                            categories.save(
                                                                    com.ikae.snowthing.domain.post
                                                                            .entity.PostCategory
                                                                            .builder()
                                                                            .code("MARKET")
                                                                            .name("Market")
                                                                            .build()));
                            var category =
                                    marketCategories
                                            .findByCode("SNOWBOARD")
                                            .orElseGet(
                                                    () ->
                                                            marketCategories.save(
                                                                    com.ikae.snowthing.domain.market
                                                                            .entity.MarketCategory
                                                                            .builder()
                                                                            .code("SNOWBOARD")
                                                                            .name("Snowboard")
                                                                            .active(true)
                                                                            .build()));
                            var post =
                                    posts.save(
                                            com.ikae.snowthing.domain.post.entity.Post.builder()
                                                    .member(member)
                                                    .category(postCategory)
                                                    .title("market thumbnail " + unique)
                                                    .content(content)
                                                    .writerIp("192.0.2.0")
                                                    .isAnonymous(false)
                                                    .build());
                            listingRepository.save(
                                    com.ikae.snowthing.domain.market.entity.MarketListing.builder()
                                            .post(post)
                                            .category(category)
                                            .productCondition(
                                                    com.ikae.snowthing.domain.market.entity
                                                            .ProductCondition.GOOD)
                                            .transactionMethod(
                                                    com.ikae.snowthing.domain.market.entity
                                                            .TransactionMethod.DIRECT)
                                            .price(10000L)
                                            .contact("test-contact")
                                            .build());
                            return new Seed(member, post.getPublicId());
                        });
    }

    private void removeListing(String publicId) {
        jdbc.update(
                "DELETE m FROM market_listing m JOIN post p ON p.post_id=m.post_id WHERE p.public_id=?",
                publicId);
        jdbc.update("DELETE FROM market_thumbnail WHERE post_public_id=?", publicId);
        jdbc.update("DELETE FROM post WHERE public_id=?", publicId);
    }

    private String previewThumbnail(String publicId) {
        return listings.getPreview().items().stream()
                .filter(item -> item.publicId().equals(publicId))
                .findFirst()
                .orElseThrow()
                .thumbnailImageUrl();
    }

    private void runBackfillMigration() throws java.io.IOException {
        jdbc.execute(
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "../database/manual/ika36_market_inline_thumbnail_backfill.sql")));
    }

    @Test
    void backfilledInlineListingShowsThumbnailOnEveryScreenWithoutChangingContent()
            throws java.io.IOException {
        String content = "설명 ![a](data:image/png;base64,QUJD) 끝";
        Seed seed = seedListing(content);
        String listingId = seed.publicId();
        String target = "public/posts/thumbnails/market-" + listingId + ".jpg";
        try {
            assertThat(previewThumbnail(listingId)).isNull();

            runBackfillMigration();
            thumbnails.processOne(listingId);

            verify(images)
                    .createMarketThumbnailFromBytes(
                            eq("ABC".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                            eq(target),
                            any(ImageProcessingGate.class));
            assertThat(previewThumbnail(listingId)).endsWith(target);
            assertThat(
                            listings.search(1, 50, null, null, null, null).content().stream()
                                    .filter(item -> item.publicId().equals(listingId))
                                    .findFirst()
                                    .orElseThrow()
                                    .thumbnailImageUrl())
                    .endsWith(target);
            var detail = listings.getDetail(listingId, seed.user(), false);
            assertThat(detail.images()).isEmpty();
            assertThat(detail.coverImageUrl()).endsWith(target);
            assertThat(detail.content()).isEqualTo(content);

            // 이미 끝난 작업은 다시 실행해도 건드리지 않는다.
            runBackfillMigration();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT revision FROM market_thumbnail WHERE post_public_id=?",
                                    Long.class,
                                    listingId))
                    .isZero();
        } finally {
            removeListing(listingId);
        }
    }

    @Test
    void backfillSkipsListingsWithoutInlineOnlyPhoto() throws java.io.IOException {
        Seed plain = seedListing("사진 없는 본문");
        Seed withKey =
                seedListing(
                        "![a](data:image/png;base64,QUJD) ![b](https://images.snowthing.org/"
                                + first
                                + ")");
        try {
            runBackfillMigration();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM market_thumbnail WHERE post_public_id IN (?,?)",
                                    Long.class,
                                    plain.publicId(),
                                    withKey.publicId()))
                    .isZero();
        } finally {
            removeListing(plain.publicId());
            removeListing(withKey.publicId());
        }
    }

    @Test
    void changedFirstInlineImageRegeneratesThumbnail() {
        String content = "![a](data:image/png;base64,QUJD)";
        String changed = "![a](data:image/png;base64,REVG)";
        Seed seed = seedListing(content);
        String listingId = seed.publicId();
        try {
            new TransactionTemplate(manager)
                    .executeWithoutResult(
                            status ->
                                    thumbnails.queue(listingId, thumbnails.inlineSource(content)));
            thumbnails.processOne(listingId);
            assertThat(thumbnails.readyUrls(List.of(listingId))).containsKey(listingId);
            jdbc.update("UPDATE post SET content=? WHERE public_id=?", changed, listingId);
            new TransactionTemplate(manager)
                    .executeWithoutResult(
                            status ->
                                    thumbnails.queue(listingId, thumbnails.inlineSource(changed)));
            assertThat(thumbnails.readyUrls(List.of(listingId))).isEmpty();
            thumbnails.processOne(listingId);
            verify(images)
                    .createMarketThumbnailFromBytes(
                            eq("DEF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                            anyString(),
                            any(ImageProcessingGate.class));
            assertThat(thumbnails.readyUrls(List.of(listingId))).containsKey(listingId);
        } finally {
            removeListing(listingId);
        }
    }

    @Test
    void brokenInlineImageStopsWithoutRetry() {
        String content = "![a](data:image/png;base64,A)";
        Seed seed = seedListing(content);
        String listingId = seed.publicId();
        try {
            new TransactionTemplate(manager)
                    .executeWithoutResult(
                            status ->
                                    thumbnails.queue(listingId, thumbnails.inlineSource(content)));
            assertThatThrownBy(() -> thumbnails.processOne(listingId))
                    .isInstanceOf(com.ikae.snowthing.global.exception.CustomException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT due_at FROM market_thumbnail WHERE post_public_id=?",
                                    java.sql.Timestamp.class,
                                    listingId))
                    .isNull();
            assertThat(previewThumbnail(listingId)).isNull();
            verifyNoInteractions(images);
        } finally {
            removeListing(listingId);
        }
    }

    @Test
    void bodyPhotoFillsDetailCoverWithoutJoiningGalleryImages() {
        String key = "public/posts/originals/m-" + UUID.randomUUID() + ".jpg";
        Seed seed = seedListing("![a](https://images.snowthing.org/" + key + ")");
        try {
            var detail = listings.getDetail(seed.publicId(), seed.user(), false);
            assertThat(detail.images()).isEmpty();
            assertThat(detail.coverImageUrl()).endsWith(key);
        } finally {
            removeListing(seed.publicId());
        }
    }

    private void queue(String key) {
        new TransactionTemplate(manager).executeWithoutResult(status -> thumbnails.queue(id, key));
    }

    @Test
    void deletingThumbnailKeepsRetryingPastAttemptLimit() {
        doThrow(new IllegalStateException("test failure"))
                .when(s3)
                .deleteObject(any(DeleteObjectRequest.class));
        queue(null);
        for (int attempt = 0; attempt < 7; attempt++) {
            jdbc.update(
                    "UPDATE market_thumbnail SET due_at='2000-01-01',lease_until=NULL WHERE post_public_id=?",
                    id);
            assertThatThrownBy(() -> thumbnails.processOne(id))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT due_at IS NOT NULL FROM market_thumbnail WHERE post_public_id=?",
                                    Boolean.class,
                                    id))
                    .isTrue();
        }
    }

    @Test
    void onlyRepresentativeIsGeneratedAndReplacementReusesOneObjectKey() {
        queue(first);
        verifyNoInteractions(images);
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();
        thumbnails.processOne(id);
        String target = "public/posts/thumbnails/market-" + id + ".jpg";
        verify(images).createMarketThumbnail(eq(first), eq(target), any(ImageProcessingGate.class));
        assertThat(thumbnails.readyUrls(List.of(id)).get(id)).endsWith(target);
        queue(second);
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();
        thumbnails.processOne(id);
        verify(images)
                .createMarketThumbnail(eq(second), eq(target), any(ImageProcessingGate.class));
        queue(second);
        thumbnails.processOne(id);
        verifyNoMoreInteractions(images);
        queue(null);
        thumbnails.processOne(id);
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();
    }

    @Test
    void rolledBackPostDoesNotCreateAThumbnailJobOrAnObject() {
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            thumbnails.queue(id, first);
                            status.setRollbackOnly();
                        });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM market_thumbnail WHERE post_public_id=?",
                                Long.class,
                                id))
                .isZero();
        thumbnails.processOne(id);
        verifyNoInteractions(images, s3);
    }

    @Test
    void failedGenerationRetriesAndBudgetFailureCannotCallS3() {
        queue(first);
        doThrow(new IllegalStateException("test failure"))
                .when(images)
                .createMarketThumbnail(anyString(), anyString(), any(ImageProcessingGate.class));
        assertThatThrownBy(() -> thumbnails.processOne(id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();
        reset(images);
        jdbc.update(
                "UPDATE market_thumbnail SET due_at='2000-01-01',lease_until=NULL WHERE post_public_id=?",
                id);
        when(limits.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(false, 60));
        thumbnails.processOne(id);
        verifyNoInteractions(images, s3);
        when(limits.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));
        jdbc.update("UPDATE market_thumbnail SET due_at='2000-01-01' WHERE post_public_id=?", id);
        thumbnails.processOne(id);
        assertThat(thumbnails.readyUrls(List.of(id))).hasSize(1);
    }

    @Test
    void newerRevisionQueuedDuringGenerationIsGeneratedNext() {
        queue(first);
        doAnswer(
                        invocation -> {
                            queue(second);
                            return null;
                        })
                .when(images)
                .createMarketThumbnail(eq(first), anyString(), any(ImageProcessingGate.class));
        thumbnails.processOne(id);
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();
        thumbnails.processOne(id);
        verify(images)
                .createMarketThumbnail(eq(second), anyString(), any(ImageProcessingGate.class));
        assertThat(thumbnails.readyUrls(List.of(id))).containsKey(id);
    }

    @Test
    void futureLeasePreventsDuplicateGeneration() {
        queue(first);
        jdbc.update(
                "UPDATE market_thumbnail SET lease_until='2099-01-01' WHERE post_public_id=?", id);
        thumbnails.processOne(id);
        verifyNoInteractions(images, limits, s3);
    }

    @Test
    void staleFailureCannotOverwriteNewerQueue() {
        queue(first);
        doAnswer(
                        invocation -> {
                            queue(second);
                            throw new IllegalStateException("simulated failure");
                        })
                .when(images)
                .createMarketThumbnail(eq(first), anyString(), any(ImageProcessingGate.class));
        assertThatThrownBy(() -> thumbnails.processOne(id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts FROM market_thumbnail WHERE post_public_id=?",
                                Integer.class,
                                id))
                .isZero();
        thumbnails.processOne(id);
        verify(images)
                .createMarketThumbnail(eq(second), anyString(), any(ImageProcessingGate.class));
    }

    @Test
    void permanentFailureStopsAndQueueResetsAttempts() {
        queue(first);
        doThrow(
                        software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder()
                                .statusCode(404)
                                .build())
                .when(images)
                .createMarketThumbnail(anyString(), anyString(), any(ImageProcessingGate.class));
        assertThatThrownBy(() -> thumbnails.processOne(id)).isInstanceOf(RuntimeException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT due_at FROM market_thumbnail WHERE post_public_id=?",
                                java.sql.Timestamp.class,
                                id))
                .isNull();
        thumbnails.processOne(id);
        verify(images, times(1))
                .createMarketThumbnail(anyString(), anyString(), any(ImageProcessingGate.class));
        queue(second);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts FROM market_thumbnail WHERE post_public_id=?",
                                Integer.class,
                                id))
                .isZero();
    }

    @Test
    void transientFailuresBackOffAndStopAfterFiveAttempts() {
        queue(first);
        doThrow(new IllegalStateException("simulated failure"))
                .when(images)
                .createMarketThumbnail(anyString(), anyString(), any(ImageProcessingGate.class));
        for (int attempt = 1; attempt <= 5; attempt++) {
            jdbc.update(
                    "UPDATE market_thumbnail SET due_at='2000-01-01' WHERE post_public_id=?", id);
            var before = java.time.Instant.now();
            assertThatThrownBy(() -> thumbnails.processOne(id))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT attempts FROM market_thumbnail WHERE post_public_id=?",
                                    Integer.class,
                                    id))
                    .isEqualTo(attempt);
            var due =
                    jdbc.queryForObject(
                            "SELECT due_at FROM market_thumbnail WHERE post_public_id=?",
                            java.sql.Timestamp.class,
                            id);
            if (attempt == 5) assertThat(due).isNull();
            else
                assertThat(due.toInstant())
                        .isAfterOrEqualTo(before.plusSeconds(60L << (attempt - 1)));
        }
    }

    @Test
    void budgetDenialUsesRetryAfterWithoutCountingAsFailure() {
        queue(first);
        when(limits.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 1800));
        var before = java.time.Instant.now();
        thumbnails.processOne(id);
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT due_at FROM market_thumbnail WHERE post_public_id=?",
                                        java.sql.Timestamp.class,
                                        id)
                                .toInstant())
                .isAfterOrEqualTo(before.plusSeconds(1800));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT attempts FROM market_thumbnail WHERE post_public_id=?",
                                Integer.class,
                                id))
                .isZero();
        verifyNoInteractions(images, s3);
    }

    @Test
    void orphanCleanupDrainsMultipleBatchesAndDefersFailedDeletes() {
        jdbc.update("DELETE FROM market_thumbnail");
        for (int i = 0; i < 45; i++)
            jdbc.update(
                    "INSERT INTO market_thumbnail(post_public_id,revision,attempts) VALUES(?,0,0)",
                    UUID.randomUUID().toString());
        doThrow(new IllegalStateException("simulated S3 failure"))
                .doReturn(null)
                .when(s3)
                .deleteObject(any(DeleteObjectRequest.class));
        thumbnails.cleanupOrphans();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM market_thumbnail", Long.class))
                .isEqualTo(1);
        verify(s3, times(45)).deleteObject(any(DeleteObjectRequest.class));
        thumbnails.cleanupOrphans();
        verifyNoMoreInteractions(s3);
    }

    @Test
    void physicallyRemovedPostThumbnailIsCollected() {
        queue(first);
        thumbnails.processOne(id);
        posts.delete(posts.findByPublicId(id).orElseThrow());
        thumbnails.cleanupOrphans();
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        jdbc.update("DELETE FROM post WHERE public_id=?", id);
        thumbnails.cleanupOrphans();
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM market_thumbnail WHERE post_public_id=?",
                                Long.class,
                                id))
                .isZero();
    }
}
