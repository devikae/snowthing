package com.ikae.snowthing.domain.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.image.service.MarketThumbnailService;
import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.domain.market.dto.MarketListingCreateRequest;
import com.ikae.snowthing.domain.market.entity.MarketCategory;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/** S3는 대역이다. 실제 AWS에 요청하지 않으며, 대역이 받은 호출로 삭제 여부를 확인한다. */
@SpringBootTest(
        properties = {
            "snowthing.post-images.cleanup-delay-ms=36000000",
            "snowthing.market-thumbnails.delay-ms=36000000"
        })
@AutoConfigureMockMvc
class PostImagePurgeIntegrationTest {
    private static final String IMAGE_HOST = "https://images.snowthing.org/";

    @Autowired PostImageLifecycleService lifecycle;
    @Autowired MarketThumbnailService thumbnails;
    @Autowired PostService posts;
    @Autowired MarketListingService listings;
    @Autowired MarketCategoryRepository marketCategories;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PostCategoryRepository categories;
    @Autowired MockMvc mockMvc;
    @MockitoBean S3Client s3;
    private Long writerMemberId;
    private CustomUserDetails writer;
    private CustomUserDetails admin;

    @BeforeEach
    void setup() {
        Member writerMember = member(Role.ROLE_USER);
        writerMemberId = writerMember.getId();
        writer = new CustomUserDetails(writerMember);
        admin = new CustomUserDetails(member(Role.ROLE_ADMIN));
        category("FREE");
        category("ANONYMOUS");
    }

    /** 다른 테스트의 회원 전체 삭제가 외래 키에 걸리지 않게 남은 글을 지운다. */
    @AfterEach
    void removePosts() {
        String mine = "(SELECT post_id FROM post WHERE member_id=?)";
        for (String table :
                List.of("post_image", "post_content_image_reference", "market_listing")) {
            jdbc.update("DELETE FROM " + table + " WHERE post_id IN " + mine, writerMemberId);
        }
        jdbc.update("DELETE FROM post WHERE member_id=?", writerMemberId);
    }

    private Member member(Role role) {
        return members.save(
                Member.builder()
                        .email(UUID.randomUUID() + "@test.invalid")
                        .nickname(UUID.randomUUID().toString().substring(0, 12))
                        .role(role)
                        .build());
    }

    private void category(String code) {
        categories
                .findByCode(code)
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().code(code).name(code).build()));
    }

    private String upload() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB), "png", bytes);
        String key =
                lifecycle
                        .upload(
                                new MockMultipartFile(
                                        "file", "test.png", "image/png", bytes.toByteArray()),
                                writer.getUsername())
                        .imageKey();
        clearInvocations(s3);
        return key;
    }

    private String write(String content, List<String> attachments) {
        return posts.createPost(
                        new PostCreateRequest("FREE", "title", content, false, null, attachments),
                        writer,
                        "127.0.0.1")
                .publicId();
    }

    private String inline(String key) {
        return "![photo](" + IMAGE_HOST + key + ")";
    }

    private void expire(String key) {
        jdbc.update("UPDATE post_image_upload SET due_at='2000-01-01' WHERE image_key=?", key);
    }

    private String state(String key) {
        return jdbc.queryForObject(
                "SELECT state FROM post_image_upload WHERE image_key=?", String.class, key);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private org.springframework.test.web.servlet.ResultActions purge(
            String publicId, CustomUserDetails actor, String reason) throws Exception {
        var request =
                post("/api/v1/admin/posts/" + publicId + "/images/purge")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\"}");
        return mockMvc.perform(actor == null ? request : request.with(user(actor)));
    }

    @Test
    void deletingPostQueuesBodyAndAttachmentWithoutCallingS3UntilCleanupRuns() throws Exception {
        String attached = upload();
        String body = upload();
        String id = write(inline(body), List.of(attached));

        posts.deletePost(id, null, writer);

        verifyNoInteractions(s3);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM post_content_image_reference WHERE image_key=?",
                                body))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM post_image WHERE image_url=?", attached)).isZero();
        expire(attached);
        expire(body);

        lifecycle.cleanup();

        assertThat(state(attached)).isEqualTo("DELETED");
        assertThat(state(body)).isEqualTo("DELETED");
        verify(s3, atLeast(2)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void cleanupCyclePicksUpDeletedPostWithoutWaiting() throws Exception {
        String key = upload();
        String id = write(inline(key), List.of());

        posts.deletePost(id, null, writer);

        lifecycle.cleanOne(key);

        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void keyUsedByAnotherLivePostSurvivesUntilThatPostIsAlsoDeleted() throws Exception {
        String key = upload();
        String first = write(inline(key), List.of());
        String second = write(inline(key), List.of());

        posts.deletePost(first, null, writer);
        lifecycle.cleanOne(key);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("PENDING");

        posts.deletePost(second, null, writer);
        lifecycle.cleanOne(key);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void untrackedLegacyKeyInAnotherLivePostBodyIsNotQueued() {
        String key = "public/posts/" + UUID.randomUUID() + ".jpg";
        String first = write("legacy " + key, List.of());
        String second = write("legacy " + key, List.of());
        // 참조 표가 생기기 전에 쓴 글처럼 만든다.
        jdbc.update("DELETE FROM post_content_image_reference WHERE image_key=?", key);

        posts.deletePost(first, null, writer);

        assertThat(count("SELECT COUNT(*) FROM post_image_upload WHERE image_key=?", key)).isZero();

        posts.deletePost(second, null, writer);
        lifecycle.cleanOne(key);

        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void hiddenAndBlockedPostsKeepTheirFiles() throws Exception {
        for (String hidden : List.of("HIDDEN", "BLOCKED")) {
            String key = upload();
            String id = write(inline(key), List.of());
            jdbc.update("UPDATE post SET status=? WHERE public_id=?", hidden, id);
            expire(key);

            lifecycle.cleanOne(key);

            verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
            assertThat(state(key)).isEqualTo("PENDING");
        }
    }

    @Test
    void failedDeleteOfDeletedPostFileIsRetried() throws Exception {
        String key = upload();
        posts.deletePost(write(inline(key), List.of()), null, writer);
        doThrow(new IllegalStateException("test failure"))
                .when(s3)
                .deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> lifecycle.cleanOne(key)).isInstanceOf(IllegalStateException.class);
        assertThat(state(key)).isEqualTo("DELETING");

        lifecycle.cleanOne(key);
        assertThat(state(key)).isEqualTo("DELETING");

        reset(s3);
        expire(key);
        lifecycle.cleanOne(key);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void guestAnonymousPostDeleteQueuesFileWithoutMember() {
        String key = "public/posts/originals/" + UUID.randomUUID() + ".jpg";
        String id =
                posts.createPost(
                                new PostCreateRequest(
                                        "ANONYMOUS",
                                        "guest",
                                        inline(key),
                                        true,
                                        "guest-password",
                                        List.of()),
                                null,
                                "127.0.0.1")
                        .publicId();

        posts.deletePost(id, "guest-password", null);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT owner_email FROM post_image_upload WHERE image_key=?",
                                String.class,
                                key))
                .isEqualTo("legacy");
        lifecycle.cleanOne(key);
        // 옛 키는 원본과 썸네일을 함께 지운다.
        verify(s3, times(2)).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void oldPostWithMoreThanThirtyImagesCanStillBeDeleted() {
        String id = write("body", List.of());
        String many =
                java.util.stream.IntStream.range(0, 31)
                        .mapToObj(i -> "public/posts/" + UUID.randomUUID() + ".jpg")
                        .collect(java.util.stream.Collectors.joining(" "));
        jdbc.update("UPDATE post SET content=? WHERE public_id=?", many, id);

        assertThatCode(() -> posts.deletePost(id, null, writer)).doesNotThrowAnyException();

        assertThat(
                        count(
                                "SELECT COUNT(*) FROM post_image_upload WHERE image_key IN (?,?)",
                                many.split(" ")[0],
                                many.split(" ")[30]))
                .isEqualTo(2);
    }

    @Test
    void postsDeletedBeforeThisChangeStayProtectedUntilManualPurge() throws Exception {
        String key = upload();
        String id = write(inline(key), List.of());
        // 이 변경 전에 삭제된 글은 참조가 남아 있다.
        jdbc.update(
                "UPDATE post SET is_deleted=true,status='DELETED',deleted_at=NOW() WHERE public_id=?",
                id);
        expire(key);

        lifecycle.cleanOne(key);

        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("PENDING");

        purge(id, admin, "takedown")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedKeys[0]").value(key));
        lifecycle.cleanOne(key);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void marketListingDeleteRemovesThumbnailObject() throws Exception {
        String key = upload();
        category("MARKET");
        if (marketCategories.findByCode("SNOWBOARD").isEmpty()) {
            marketCategories.save(
                    MarketCategory.builder()
                            .code("SNOWBOARD")
                            .name("Snowboard")
                            .active(true)
                            .build());
        }
        String id =
                listings.create(
                                new MarketListingCreateRequest(
                                        "SNOWBOARD",
                                        "listing",
                                        "body",
                                        ProductCondition.GOOD,
                                        TransactionMethod.DIRECT,
                                        10000L,
                                        false,
                                        false,
                                        "contact",
                                        List.of(key)),
                                writer,
                                "127.0.0.1")
                        .publicId();

        listings.delete(id, writer);

        verifyNoInteractions(s3);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM market_thumbnail WHERE post_public_id=? AND source_key IS NULL AND due_at IS NOT NULL",
                                id))
                .isEqualTo(1);

        thumbnails.processOne(id);

        verify(s3).deleteObject(any(DeleteObjectRequest.class));
        assertThat(thumbnails.readyUrls(List.of(id))).isEmpty();

        lifecycle.cleanOne(key);

        assertThat(state(key)).isEqualTo("DELETED");
    }

    @Test
    void adminPurgeKeepsPostAndLeavesItEditable() throws Exception {
        String key = upload();
        String attached = upload();
        String id = write("before " + inline(key) + " after", List.of(attached));

        purge(id, admin, "report")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedKeys.length()").value(2))
                .andExpect(jsonPath("$.sharedKeys.length()").value(0));

        verifyNoInteractions(s3);
        var detail = posts.getPostDetail(id, writer, false);
        assertThat(detail.content()).isEqualTo("before  after");
        assertThat(detail.content()).doesNotContain(key);
        assertThat(detail.images()).isEmpty();
        lifecycle.cleanup();
        assertThat(state(key)).isEqualTo("DELETED");
        assertThat(state(attached)).isEqualTo("DELETED");

        assertThatCode(
                        () ->
                                posts.updatePost(
                                        id,
                                        new PostUpdateRequest(
                                                "FREE",
                                                "title",
                                                detail.content() + " edited",
                                                null,
                                                List.of()),
                                        writer))
                .doesNotThrowAnyException();
    }

    @Test
    void adminPurgeAlsoRemovesInlineBase64PhotoFromBody() throws Exception {
        String id = write("body", List.of());
        jdbc.update(
                "UPDATE post SET content=? WHERE public_id=?",
                "before ![a](data:image/png;base64,AAAA) after",
                id);

        purge(id, admin, "report").andExpect(status().isOk());

        assertThat(posts.getPostDetail(id, writer, false).content()).isEqualTo("before  after");
    }

    @Test
    void adminPurgeReportsSharedKeyAndDoesNotDeleteIt() throws Exception {
        String key = upload();
        String target = write(inline(key), List.of());
        String other = write(inline(key), List.of());

        purge(target, admin, "report")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queuedKeys.length()").value(0))
                .andExpect(jsonPath("$.sharedKeys[0]").value(key));

        lifecycle.cleanOne(key);
        verify(s3, never()).deleteObject(any(DeleteObjectRequest.class));
        assertThat(posts.getPostDetail(other, writer, false).content()).contains(key);
    }

    @Test
    void adminPurgeRequiresAdminAndExistingPostAndReason() throws Exception {
        String key = upload();
        String id = write(inline(key), List.of());

        purge(id, null, "report").andExpect(status().isUnauthorized());
        purge(id, writer, "report").andExpect(status().isForbidden());
        purge(id, admin, " ").andExpect(status().isBadRequest());
        purge(UUID.randomUUID().toString(), admin, "report").andExpect(status().isNotFound());

        assertThat(posts.getPostDetail(id, writer, false).content()).contains(key);
        assertThat(state(key)).isEqualTo("PENDING");
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM post_content_image_reference WHERE image_key=?",
                                key))
                .isEqualTo(1);
    }
}
