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

    private void queue(String key) {
        new TransactionTemplate(manager).executeWithoutResult(status -> thumbnails.queue(id, key));
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
        assertThatThrownBy(() -> thumbnails.processOne(id))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomException.class);
        verifyNoInteractions(images, s3);
        when(limits.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));
        jdbc.update("UPDATE market_thumbnail SET due_at='2000-01-01' WHERE post_public_id=?", id);
        thumbnails.processOne(id);
        assertThat(thumbnails.readyUrls(List.of(id))).hasSize(1);
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
