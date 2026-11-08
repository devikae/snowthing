package com.ikae.snowthing.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 작성 API Idempotency-Key 통합 테스트 (IKA-21).
 *
 * <p>동시 요청이 서로 다른 트랜잭션의 커밋을 봐야 하므로 테스트 트랜잭션으로 감싸지 않는다. 만든 행은 tearDown에서 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdempotentCreateIntegrationTest {

    private static final String POSTS = "/api/v1/posts";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostService postService;
    @Autowired private IdempotentCreateHandler handler;
    @Autowired private IdempotencyRecordCleanupScheduler cleanupScheduler;
    @Autowired private ContentWriteRateLimiter contentWriteRateLimiter;

    private final List<String> authorKeys = new ArrayList<>();
    private String marker;
    private Member member;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        categories
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        marker = "idem-" + UUID.randomUUID().toString().substring(0, 8);
        member =
                members.save(
                        Member.builder()
                                .email(marker + "@example.com")
                                .password("encoded")
                                .nickname(marker)
                                .role(Role.ROLE_USER)
                                .build());
        userDetails = new CustomUserDetails(member);
        authorKeys.add("member:" + member.getId());
        authorKeys.add(handler.authorKey(null, "127.0.0.1"));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(contentWriteRateLimiter, "enabled", false);
        for (String authorKey : authorKeys) {
            jdbc.update("DELETE FROM idempotency_record WHERE author_key = ?", authorKey);
        }
        List<Long> postIds =
                jdbc.queryForList(
                        "SELECT post_id FROM post WHERE title LIKE ? OR member_id = ?",
                        Long.class,
                        marker + "%",
                        member.getId());
        for (Long postId : postIds) {
            jdbc.update("DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        jdbc.update("DELETE FROM member WHERE member_id = ?", member.getId());
    }

    @Test
    @DisplayName("같은 키로 두 번 보내면 글은 1건이고 두 응답 모두 201과 같은 본문")
    void sameKeyReplaysFirstResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = postBody(marker + " 첫 글");

        String first = createPost(key, body).andExpect(status().isCreated()).andReturnBody();
        String second = createPost(key, body).andExpect(status().isCreated()).andReturnBody();

        assertThat(second).isEqualTo(first);
        assertThat(postCount()).isEqualTo(1);
        assertThat(recordCount(key)).isEqualTo(1);
    }

    @Test
    @DisplayName("대문자 키도 소문자로 맞춰 같은 키로 본다")
    void keyIsCaseInsensitive() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = postBody(marker + " 대소문자");

        createPost(key, body).andExpect(status().isCreated());
        createPost(key.toUpperCase(), body).andExpect(status().isCreated());

        assertThat(postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키에 다른 내용이면 409")
    void sameKeyDifferentBodyConflicts() throws Exception {
        String key = UUID.randomUUID().toString();
        createPost(key, postBody(marker + " 원래 글")).andExpect(status().isCreated());

        createPost(key, postBody(marker + " 바뀐 글"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_002"));
        assertThat(postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("키 형식이 UUID가 아니면 400이고 글을 저장하지 않는다")
    void invalidKeyIsRejected() throws Exception {
        createPost("not-a-uuid", postBody(marker + " 잘못된 키"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_001"));
        createPost(" ", postBody(marker + " 빈 키")).andExpect(status().isBadRequest());

        assertThat(postCount()).isZero();
    }

    @Test
    @DisplayName("헤더가 없으면 예전처럼 보낼 때마다 저장한다")
    void withoutHeaderBehavesAsBefore() throws Exception {
        String body = postBody(marker + " 헤더 없음");

        createPost(null, body).andExpect(status().isCreated());
        createPost(null, body).andExpect(status().isCreated());

        assertThat(postCount()).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM idempotency_record WHERE author_key = ?",
                                Integer.class,
                                "member:" + member.getId()))
                .isZero();
    }

    @Test
    @DisplayName("같은 키라도 게시글과 댓글은 서로 다른 요청으로 본다")
    void targetsAreSeparated() throws Exception {
        String key = UUID.randomUUID().toString();
        String postJson =
                createPost(key, postBody(marker + " 대상 분리"))
                        .andExpect(status().isCreated())
                        .andReturnBody();
        String publicId = postJson.replaceAll(".*\"publicId\":\"([^\"]+)\".*", "$1");

        createComment(publicId, key, "{\"content\":\"댓글\",\"isAnonymous\":false}")
                .andExpect(status().isCreated());

        assertThat(commentCount(publicId)).isEqualTo(1);
        assertThat(recordCount(key)).isEqualTo(2);
    }

    @Test
    @DisplayName("대댓글도 같은 키로 다시 보내면 1건만 저장한다")
    void replyReplay() throws Exception {
        PostResponse target = createPostDirectly(marker + " 대댓글 대상");
        String parentJson =
                createComment(
                                target.publicId(),
                                UUID.randomUUID().toString(),
                                "{\"content\":\"부모\",\"isAnonymous\":false}")
                        .andExpect(status().isCreated())
                        .andReturnBody();
        assertThat(parentJson).contains("부모");
        Long parentId =
                jdbc.queryForObject(
                        "SELECT c.comment_id FROM comment c JOIN post p ON p.post_id = c.post_id"
                                + " WHERE p.public_id = ?",
                        Long.class,
                        target.publicId());
        String key = UUID.randomUUID().toString();
        String reply = "{\"parentId\":" + parentId + ",\"content\":\"대댓글\",\"isAnonymous\":false}";

        String first =
                createComment(target.publicId(), key, reply)
                        .andExpect(status().isCreated())
                        .andReturnBody();
        String second =
                createComment(target.publicId(), key, reply)
                        .andExpect(status().isCreated())
                        .andReturnBody();

        assertThat(second).isEqualTo(first);
        assertThat(commentCount(target.publicId())).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 키라도 다른 게시글에 다는 댓글이면 409")
    void commentOnOtherPostConflicts() throws Exception {
        PostResponse a = createPostDirectly(marker + " 댓글 A");
        PostResponse b = createPostDirectly(marker + " 댓글 B");
        String key = UUID.randomUUID().toString();
        String body = "{\"content\":\"같은 내용\",\"isAnonymous\":false}";

        createComment(a.publicId(), key, body).andExpect(status().isCreated());
        createComment(b.publicId(), key, body).andExpect(status().isConflict());

        assertThat(commentCount(b.publicId())).isZero();
    }

    @Test
    @DisplayName("비로그인 작성도 IP 기준으로 같은 키를 1건만 저장한다")
    void anonymousReplay() throws Exception {
        categories
                .findByCode("ANONYMOUS")
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder()
                                                .name("익명게시판")
                                                .code("ANONYMOUS")
                                                .build()));
        String key = UUID.randomUUID().toString();
        String body =
                "{\"categoryCode\":\"ANONYMOUS\",\"title\":\""
                        + marker
                        + " 익명\",\"content\":\"본문\",\"isAnonymous\":true,"
                        + "\"anonymousPassword\":\"pass1234\"}";
        MockHttpServletRequestBuilder request =
                post(POSTS)
                        .with(csrf())
                        .header(IdempotentCreateHandler.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);

        mockMvc.perform(request).andExpect(status().isCreated());
        mockMvc.perform(request).andExpect(status().isCreated());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post WHERE title = ?",
                                Integer.class,
                                marker + " 익명"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("재전송은 쓰기 제한보다 먼저 확인해 429 대신 처음 응답을 받는다")
    void replayIsCheckedBeforeWriteLimit() throws Exception {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS request_budget_window (budget_key VARCHAR(100) PRIMARY KEY,used_count INT NOT NULL,expires_at_millis BIGINT NOT NULL)");
        ReflectionTestUtils.setField(contentWriteRateLimiter, "enabled", true);
        try {
            String key = UUID.randomUUID().toString();
            String body = postBody(marker + " 제한 순서");

            createPost(key, body).andExpect(status().isCreated());
            createPost(key, body).andExpect(status().isCreated());
            createPost(UUID.randomUUID().toString(), postBody(marker + " 새 키"))
                    .andExpect(status().isTooManyRequests());

            assertThat(postCount()).isEqualTo(1);
        } finally {
            ReflectionTestUtils.setField(contentWriteRateLimiter, "enabled", false);
            jdbc.update(
                    "DELETE FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:write:%'");
        }
    }

    @Test
    @DisplayName("작성이 실패하면 기록도 함께 롤백돼 같은 키로 다시 시도할 수 있다")
    void failedWriteRollsBackRecord() throws Exception {
        String key = UUID.randomUUID().toString();
        String missingCategory =
                "{\"categoryCode\":\"NO_SUCH\",\"title\":\""
                        + marker
                        + " 실패\",\"content\":\"본문\",\"isAnonymous\":false}";

        createPost(key, missingCategory).andExpect(status().is4xxClientError());
        assertThat(recordCount(key)).isZero();

        createPost(key, postBody(marker + " 재시도")).andExpect(status().isCreated());
        assertThat(postCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키로 동시에 보내도 글은 1건이고 모두 같은 글을 돌려받는다")
    void concurrentSameKeyCreatesOnce() throws Exception {
        int threads = 8;
        String key = UUID.randomUUID().toString();
        PostCreateRequest request =
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title(marker + " 동시")
                        .content("본문")
                        .isAnonymous(false)
                        .build();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<Object>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<ResponseEntity<Object>> task =
                        () -> {
                            ready.countDown();
                            start.await();
                            return handler.create(
                                    IdempotencyTarget.POST,
                                    key,
                                    userDetails,
                                    "127.0.0.1",
                                    request,
                                    () -> {},
                                    () ->
                                            postService.createPost(
                                                    request, userDetails, "127.0.0.1"));
                        };
                futures.add(pool.submit(task));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<String> publicIds = new ArrayList<>();
            for (Future<ResponseEntity<Object>> future : futures) {
                ResponseEntity<Object> response = future.get(60, TimeUnit.SECONDS);
                assertThat(response.getStatusCode().value()).isEqualTo(201);
                Object body = response.getBody();
                publicIds.add(
                        body instanceof PostResponse created
                                ? created.publicId()
                                : new String((byte[]) body, StandardCharsets.UTF_8)
                                        .replaceAll(".*\"publicId\":\"([^\"]+)\".*", "$1"));
            }
            assertThat(publicIds).hasSize(threads);
            assertThat(publicIds.stream().distinct().count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(postCount()).isEqualTo(1);
        assertThat(recordCount(key)).isEqualTo(1);
    }

    @Test
    @DisplayName("정리 스케줄러는 보관 기간이 지난 기록만 지운다")
    void cleanupDeletesOnlyExpired() throws Exception {
        String oldKey = UUID.randomUUID().toString();
        String freshKey = UUID.randomUUID().toString();
        createPost(oldKey, postBody(marker + " 오래된 기록")).andExpect(status().isCreated());
        createPost(freshKey, postBody(marker + " 새 기록")).andExpect(status().isCreated());
        jdbc.update(
                "UPDATE idempotency_record SET created_at = ? WHERE idempotency_key = ?",
                LocalDateTime.now().minusHours(25),
                oldKey);

        cleanupScheduler.deleteExpiredBefore(LocalDateTime.now().minusHours(24));

        assertThat(recordCount(oldKey)).isZero();
        assertThat(recordCount(freshKey)).isEqualTo(1);
    }

    private ResultActionsWithBody createPost(String key, String body) throws Exception {
        MockHttpServletRequestBuilder request =
                post(POSTS)
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);
        if (key != null) {
            request.header(IdempotentCreateHandler.HEADER, key);
        }
        return new ResultActionsWithBody(mockMvc.perform(request));
    }

    private ResultActionsWithBody createComment(String postPublicId, String key, String body)
            throws Exception {
        return new ResultActionsWithBody(
                mockMvc.perform(
                        post(POSTS + "/" + postPublicId + "/comments")
                                .with(csrf())
                                .with(user(userDetails))
                                .header(IdempotentCreateHandler.HEADER, key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)));
    }

    private PostResponse createPostDirectly(String title) {
        return postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title(title)
                        .content("본문")
                        .isAnonymous(false)
                        .build(),
                userDetails,
                "127.0.0.1");
    }

    private String postBody(String title) {
        return "{\"categoryCode\":\"FREE\",\"title\":\""
                + title
                + "\",\"content\":\"본문\",\"isAnonymous\":false}";
    }

    private int postCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM post WHERE member_id = ? AND title LIKE ?",
                Integer.class,
                member.getId(),
                marker + "%");
    }

    private int commentCount(String postPublicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM comment c JOIN post p ON p.post_id = c.post_id"
                        + " WHERE p.public_id = ?",
                Integer.class,
                postPublicId);
    }

    private int recordCount(String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_record WHERE idempotency_key = ?",
                Integer.class,
                key.toLowerCase());
    }

    /** MockMvc 결과에서 UTF-8 본문을 꺼내기 쉽게 감싼다. */
    private record ResultActionsWithBody(
            org.springframework.test.web.servlet.ResultActions actions) {
        ResultActionsWithBody andExpect(org.springframework.test.web.servlet.ResultMatcher matcher)
                throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        String andReturnBody() throws Exception {
            return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        }
    }
}
