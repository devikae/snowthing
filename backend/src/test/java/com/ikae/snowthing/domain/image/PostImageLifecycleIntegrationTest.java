package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@SpringBootTest(properties = "snowthing.post-images.cleanup-delay-ms=36000000")
class PostImageLifecycleIntegrationTest {
    @Autowired PostImageLifecycleService lifecycle;
    @Autowired PostService posts;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PostCategoryRepository categories;
    @MockitoBean S3Client s3;
    private CustomUserDetails user;

    @BeforeEach
    void setup() {
        user =
                new CustomUserDetails(
                        members.save(
                                Member.builder()
                                        .email(UUID.randomUUID() + "@test.invalid")
                                        .nickname(UUID.randomUUID().toString().substring(0, 12))
                                        .role(Role.ROLE_USER)
                                        .build()));
        categories
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().code("FREE").name("FREE").build()));
    }

    private String upload() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return lifecycle
                .upload(
                        new MockMultipartFile("file", "test.png", "image/png", bytes.toByteArray()),
                        user.getUsername())
                .imageKey();
    }

    private void expire(String key) {
        jdbc.update("UPDATE post_image_upload SET due_at='2000-01-01' WHERE image_key=?", key);
    }

    private String state(String key) {
        return jdbc.queryForObject(
                "SELECT state FROM post_image_upload WHERE image_key=?", String.class, key);
    }

    @Test
    void abandonedUploadDeletesOriginalAndRejectsStaleAttachment() throws Exception {
        String key = upload();
        expire(key);
        lifecycle.cleanOne(key);
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("DELETED");
        assertThatThrownBy(
                        () ->
                                posts.createPost(
                                        new PostCreateRequest(
                                                "FREE", "test", "body", false, null, List.of(key)),
                                        user,
                                        "127.0.0.1"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void referencedFileSurvivesAndRemovedAttachmentIsCollected() throws Exception {
        String key = upload();
        var post =
                posts.createPost(
                        new PostCreateRequest("FREE", "test", "body", false, null, List.of(key)),
                        user,
                        "127.0.0.1");
        expire(key);
        lifecycle.cleanOne(key);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        posts.updatePost(
                post.publicId(),
                new PostUpdateRequest("FREE", "test", "body", null, List.of()),
                user);
        expire(key);
        lifecycle.cleanOne(key);
        verify(s3, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void anonymousPasswordEditRetainsExistingPhotoButCannotIntroduceAnotherUpload()
            throws Exception {
        String key = upload();
        var post =
                posts.createPost(
                        new PostCreateRequest(
                                "FREE", "anonymous", "body", true, "test-password", List.of(key)),
                        user,
                        "127.0.0.1");
        assertThatCode(
                        () ->
                                posts.updatePost(
                                        post.publicId(),
                                        new PostUpdateRequest(
                                                "FREE",
                                                "edited",
                                                "body",
                                                "test-password",
                                                List.of(key)),
                                        null))
                .doesNotThrowAnyException();
        String another = upload();
        assertThatThrownBy(
                        () ->
                                posts.updatePost(
                                        post.publicId(),
                                        new PostUpdateRequest(
                                                "FREE",
                                                "edited",
                                                "body",
                                                "test-password",
                                                List.of(key, another)),
                                        null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void repeatedInlineKeysAreDeduplicatedAndDistinctKeysAreBounded() throws Exception {
        String key = upload();
        assertThatCode(
                        () ->
                                posts.createPost(
                                        new PostCreateRequest(
                                                "FREE",
                                                "repeated",
                                                (key + " ").repeat(100),
                                                false,
                                                null,
                                                List.of(key)),
                                        user,
                                        "127.0.0.1"))
                .doesNotThrowAnyException();
        String many =
                java.util.stream.IntStream.range(0, 31)
                        .mapToObj(i -> "public/posts/originals/m-" + UUID.randomUUID() + ".jpg")
                        .collect(java.util.stream.Collectors.joining(" "));
        assertThatThrownBy(
                        () ->
                                posts.createPost(
                                        new PostCreateRequest(
                                                "FREE", "many", many, false, null, List.of()),
                                        user,
                                        "127.0.0.1"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void failedDeleteIsRetriedAndDifferentOwnerCannotAttach() throws Exception {
        String key = upload();
        assertThatThrownBy(() -> lifecycle.renew(List.of(key), "other@test.invalid"))
                .isInstanceOf(CustomException.class);
        expire(key);
        doThrow(new IllegalStateException("test failure"))
                .when(s3)
                .deleteObject(any(DeleteObjectRequest.class));
        assertThatThrownBy(() -> lifecycle.cleanOne(key)).isInstanceOf(IllegalStateException.class);
        assertThat(state(key)).isEqualTo("DELETING");
        reset(s3);
        expire(key);
        lifecycle.cleanOne(key);
        assertThat(state(key)).isEqualTo("DELETED");
    }
}
