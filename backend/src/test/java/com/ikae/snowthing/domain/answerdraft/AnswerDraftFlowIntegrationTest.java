package com.ikae.snowthing.domain.answerdraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftPublishResponse;
import com.ikae.snowthing.domain.answerdraft.service.AnswerDraftPublishPolicy;
import com.ikae.snowthing.domain.answerdraft.service.AnswerDraftService;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.search.service.SearchTextChunker;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 제한 API 토큰과 답변 초안 흐름 통합 테스트 (IKA-12).
 *
 * <p>동시 게시 테스트가 서로 다른 트랜잭션의 커밋을 봐야 하므로 테스트 트랜잭션으로 감싸지 않는다. 만든 행은 tearDown에서 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnswerDraftFlowIntegrationTest {

    private static final String DRAFTS = "/api/v1/answer-drafts";
    private static final String ADMIN_DRAFTS = "/api/v1/admin/answer-drafts";
    private static final String TOKENS = "/api/v1/admin/api-tokens";
    private static final String EVIDENCE_TITLE = "바인딩 각도 정리";
    private static final String EVIDENCE_BODY = "덕스탠스는 전경 15도, 후경 -15도에서 시작하는 사람이 많습니다.";
    private static final String EVIDENCE_COMMENT = "저는 +18/-6으로 타는데 무릎이 편했어요.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private AnswerDraftPublishPolicy publishPolicy;
    @Autowired private AnswerDraftService answerDraftService;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();
    private Long categoryId;
    private Member admin;
    private Member secondAdmin;
    private Member user;
    private Post target;
    private Post hiddenPost;
    private Post evidencePost;
    private Comment evidenceComment;

    @BeforeEach
    void setUp() {
        transactions.executeWithoutResult(status -> createSeed());
        ReflectionTestUtils.setField(publishPolicy, "publishEnabled", true);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(publishPolicy, "publishEnabled", false);
        transactions.executeWithoutResult(
                status -> {
                    for (Long memberId : memberIds) {
                        jdbc.update(
                                "DELETE a FROM answer_draft_audit a JOIN answer_draft d"
                                        + " ON d.answer_draft_id = a.answer_draft_id"
                                        + " WHERE d.created_by_member_id = ?",
                                memberId);
                        jdbc.update(
                                "DELETE e FROM answer_draft_evidence e JOIN answer_draft d"
                                        + " ON d.answer_draft_id = e.answer_draft_id"
                                        + " WHERE d.created_by_member_id = ?",
                                memberId);
                        jdbc.update(
                                "DELETE FROM answer_draft WHERE created_by_member_id = ?",
                                memberId);
                    }
                    for (Long memberId : memberIds) {
                        jdbc.update("DELETE FROM api_token WHERE member_id = ?", memberId);
                    }
                    for (Long postId : postIds) {
                        jdbc.update(
                                "DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL",
                                postId);
                        jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
                        jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
                    }
                    jdbc.update("DELETE FROM post_category WHERE category_id = ?", categoryId);
                    for (Long memberId : memberIds) {
                        jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
                    }
                });
    }

    @Nested
    @DisplayName("초안 저장")
    class Save {

        @Test
        @DisplayName("confirmed, approved, publish, status를 보내도 게시되지 않고 REVIEW_REQUIRED로 저장된다")
        void modelConfirmationIsIgnored() throws Exception {
            String token = createToken(admin, "DRAFT");
            Map<String, Object> body = draftBody("key-confirmed-1", "답변 본문입니다.");
            body.put("confirmed", true);
            body.put("approved", true);
            body.put("publish", true);
            body.put("status", "PUBLISHED");

            JsonNode saved =
                    json(
                            mockMvc.perform(withToken(post(DRAFTS), token).content(write(body)))
                                    .andExpect(status().isCreated())
                                    .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                                    .andExpect(jsonPath("$.publishedCommentId").isEmpty())
                                    .andExpect(jsonPath("$.evidenceCount").value(2)));

            assertThat(commentCount(target.getId())).isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM answer_draft WHERE public_id = ?",
                                    String.class,
                                    saved.get("draftId").asText()))
                    .isEqualTo("REVIEW_REQUIRED");
            assertThat(auditActions(saved.get("draftId").asText())).containsExactly("SAVE");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM answer_draft_audit a JOIN answer_draft d"
                                            + " ON d.answer_draft_id = a.answer_draft_id"
                                            + " WHERE d.public_id = ? AND a.actor_token_id IS NOT NULL",
                                    Integer.class,
                                    saved.get("draftId").asText()))
                    .isEqualTo(1);

            mockMvc.perform(withToken(get(DRAFTS + "/{id}", saved.get("draftId").asText()), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REVIEW_REQUIRED"))
                    .andExpect(
                            jsonPath("$.reviewUrl")
                                    .value(
                                            "/admin/answer-drafts/"
                                                    + saved.get("draftId").asText()));
        }

        @Test
        @DisplayName("같은 멱등성 키와 같은 내용은 처음 초안을 200으로 돌려주고, 다른 내용은 409")
        void idempotentSave() throws Exception {
            String token = createToken(admin, "DRAFT");
            String first =
                    json(mockMvc.perform(
                                            withToken(post(DRAFTS), token)
                                                    .content(
                                                            write(
                                                                    draftBody(
                                                                            "key-idem-0001",
                                                                            "같은 본문"))))
                                    .andExpect(status().isCreated()))
                            .get("draftId")
                            .asText();

            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("key-idem-0001", "같은 본문"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.draftId").value(first));
            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("key-idem-0001", "다른 본문"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_004"));

            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM answer_draft WHERE created_by_member_id = ?",
                                    Integer.class,
                                    admin.getId()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("대상 게시글이 숨김이면 404, 근거 해시가 다르면 409, 형식이 틀리면 400")
        void validatesTargetAndEvidence() throws Exception {
            String token = createToken(admin, "DRAFT");
            Map<String, Object> hidden = draftBody("key-hidden-01", "본문");
            hidden.put("targetPostPublicId", hiddenPost.getPublicId());
            mockMvc.perform(withToken(post(DRAFTS), token).content(write(hidden)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DRAFT_003"));

            Map<String, Object> wrongHash = draftBody("key-wronghash", "본문");
            wrongHash.put(
                    "evidence",
                    List.of(
                            Map.of(
                                    "evidenceId",
                                    "C:" + evidenceComment.getId() + ":0",
                                    "chunkHash",
                                    "0".repeat(64))));
            mockMvc.perform(withToken(post(DRAFTS), token).content(write(wrongHash)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EVIDENCE_002"));

            Map<String, Object> noEvidence = draftBody("key-noevidence", "본문");
            noEvidence.put("evidence", List.of());
            mockMvc.perform(withToken(post(DRAFTS), token).content(write(noEvidence)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DRAFT_002"));

            Map<String, Object> tooLong = draftBody("key-toolong-1", "가".repeat(1001));
            mockMvc.perform(withToken(post(DRAFTS), token).content(write(tooLong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DRAFT_002"));

            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM answer_draft WHERE created_by_member_id = ?",
                                    Integer.class,
                                    admin.getId()))
                    .isZero();
        }

        @Test
        @DisplayName("멱등성 키는 대소문자를 구분한다")
        void idempotencyKeyIsCaseSensitive() throws Exception {
            String token = createToken(admin, "DRAFT");
            String lower = saveDraft(token, "key-case-abcd");
            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("KEY-CASE-ABCD", "다른 본문"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.draftId").value(org.hamcrest.Matchers.not(lower)));
        }

        @Test
        @DisplayName("작성자별 검토 대기 초안이 상한에 닿으면 429")
        void pendingDraftsAreCapped() throws Exception {
            String token = createToken(admin, "DRAFT");
            for (int i = 0; i < AnswerDraftService.MAX_PENDING_PER_CREATOR; i++) {
                saveDraft(token, "key-cap-" + String.format("%04d", i));
            }
            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("key-cap-over", "본문"))))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("DRAFT_012"));
            // 같은 키 재시도는 상한과 상관없이 기존 초안을 돌려준다.
            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("key-cap-0000", "답변 초안 본문"))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("토큰은 자기 소유자가 만든 초안의 상태만 볼 수 있다")
        void statusIsScopedToOwner() throws Exception {
            String ownerToken = createToken(admin, "DRAFT");
            String otherToken = createToken(secondAdmin, "DRAFT");
            String draftId = saveDraft(ownerToken, "key-owner-001");

            mockMvc.perform(withToken(get(DRAFTS + "/{id}", draftId), otherToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DRAFT_001"));
            mockMvc.perform(get(DRAFTS + "/{id}", draftId).with(user(details(secondAdmin))))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("토큰 범위")
    class TokenScope {

        @Test
        @DisplayName("READ 토큰은 근거 조회만 되고 초안 저장은 403")
        void readTokenCannotSaveDraft() throws Exception {
            String token = createToken(admin, "READ");
            mockMvc.perform(
                            withToken(
                                    get(
                                            "/api/v1/admin/search/evidence/{id}",
                                            "C:" + evidenceComment.getId() + ":0"),
                                    token))
                    .andExpect(status().isOk());
            mockMvc.perform(
                            withToken(post(DRAFTS), token)
                                    .content(write(draftBody("key-read-0001", "본문"))))
                    .andExpect(status().isForbidden());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM answer_draft WHERE created_by_member_id = ?",
                                    Integer.class,
                                    admin.getId()))
                    .isZero();
        }

        @Test
        @DisplayName("DRAFT 토큰은 승인, 거절, 게시, 목록, 토큰 발급, 근거 조회를 할 수 없다")
        void draftTokenCannotApproveOrPublish() throws Exception {
            String token = createToken(admin, "DRAFT");
            String draftId = saveDraft(token, "key-scope-001");

            for (String action : List.of("approve", "reject", "publish")) {
                mockMvc.perform(withToken(post(ADMIN_DRAFTS + "/{id}/" + action, draftId), token))
                        .andExpect(status().isForbidden());
            }
            mockMvc.perform(withToken(get(ADMIN_DRAFTS), token)).andExpect(status().isForbidden());
            mockMvc.perform(withToken(get(ADMIN_DRAFTS + "/{id}", draftId), token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            withToken(post(TOKENS), token)
                                    .content("{\"name\":\"x\",\"scopes\":[\"DRAFT\"]}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            withToken(
                                    get(
                                            "/api/v1/admin/search/evidence/{id}",
                                            "C:" + evidenceComment.getId() + ":0"),
                                    token))
                    .andExpect(status().isForbidden());

            // 승인을 우회하려는 경로도 모두 막혀 상태가 그대로다.
            assertThat(draftStatus(draftId)).isEqualTo("REVIEW_REQUIRED");
            assertThat(commentCount(target.getId())).isZero();
        }

        @Test
        @DisplayName("토큰 허용 경로가 아니면 Bearer 헤더가 있어도 CSRF 검사를 건너뛰지 않는다")
        void bearerHeaderDoesNotSkipCsrfOutsideTokenRoutes() throws Exception {
            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .with(user(details(admin)))
                                    .header(
                                            HttpHeaders.AUTHORIZATION,
                                            "Bearer snt_" + "A".repeat(43)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("폐기, 만료, 소유자 관리자 권한 상실, 형식 오류 토큰은 401")
        void rejectsRevokedExpiredDemotedAndMalformedTokens() throws Exception {
            String revoked = createToken(admin, "READ", "DRAFT");
            String tokenId = tokenIdOf(revoked);
            mockMvc.perform(
                            post(TOKENS + "/{id}/revoke", tokenId)
                                    .with(csrf())
                                    .with(user(details(admin))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            mockMvc.perform(withToken(get(DRAFTS + "/{id}", UUID.randomUUID()), revoked))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_001"));

            String expired = createToken(admin, "DRAFT");
            jdbc.update(
                    "UPDATE api_token SET expires_at = ? WHERE token_hash = ?",
                    java.time.LocalDateTime.now().minusSeconds(1),
                    ApiTokenService.hash(expired));
            mockMvc.perform(
                            withToken(post(DRAFTS), expired)
                                    .content(write(draftBody("key-expired-1", "본문"))))
                    .andExpect(status().isUnauthorized());

            String demoted = createToken(secondAdmin, "DRAFT");
            jdbc.update(
                    "UPDATE member SET role = 'ROLE_USER' WHERE member_id = ?",
                    secondAdmin.getId());
            mockMvc.perform(
                            withToken(post(DRAFTS), demoted)
                                    .content(write(draftBody("key-demoted-1", "본문"))))
                    .andExpect(status().isUnauthorized());

            String unknown = "snt_" + "A".repeat(43);
            mockMvc.perform(withToken(get(DRAFTS + "/{id}", UUID.randomUUID()), unknown))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(withToken(get(DRAFTS + "/{id}", UUID.randomUUID()), "snt_short"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("평문 토큰은 저장하지 않고 SHA-256 해시와 접두사만 남는다")
        void plaintextIsNeverStored() throws Exception {
            JsonNode created = createTokenResponse(admin, "READ");
            String plaintext = created.get("token").asText();
            assertThat(plaintext).matches("^snt_[A-Za-z0-9_-]{43}$");

            Map<String, Object> row =
                    jdbc.queryForMap(
                            "SELECT * FROM api_token WHERE public_id = ?",
                            created.get("apiToken").get("tokenId").asText());
            assertThat(row.values())
                    .allSatisfy(
                            value ->
                                    assertThat(String.valueOf(value))
                                            .doesNotContain(plaintext.substring(4)));
            assertThat(row.get("token_hash")).isEqualTo(ApiTokenService.hash(plaintext));
            assertThat(row.get("token_prefix")).isEqualTo(plaintext.substring(0, 12));

            JsonNode list =
                    json(
                            mockMvc.perform(get(TOKENS).with(user(details(admin))))
                                    .andExpect(status().isOk()));
            assertThat(list.toString()).doesNotContain(plaintext.substring(4));
        }

        @Test
        @DisplayName("일반 회원은 토큰을 만들 수 없고, 비로그인은 초안 API에 401")
        void onlyAdminsCanCreateTokens() throws Exception {
            mockMvc.perform(
                            post(TOKENS)
                                    .with(csrf())
                                    .with(user(details(user)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"name\":\"x\",\"scopes\":[\"READ\"]}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            post(DRAFTS)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(write(draftBody("key-anon-0001", "본문"))))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            post(DRAFTS)
                                    .with(csrf())
                                    .with(user(details(user)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(write(draftBody("key-user-0001", "본문"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("승인과 게시")
    class Publish {

        @Test
        @DisplayName("승인한 초안을 게시하면 승인자 이름의 댓글 하나가 생기고, 다시 게시해도 같은 댓글을 돌려준다")
        void publishesOnceAndReplays() throws Exception {
            String draftId = approvedDraft("key-publish-1");

            JsonNode first = json(publish(draftId, secondAdmin).andExpect(status().isOk()));
            assertThat(first.get("status").asText()).isEqualTo("PUBLISHED");
            assertThat(first.get("alreadyPublished").asBoolean()).isFalse();
            String commentId = first.get("publishedCommentId").asText();

            JsonNode second = json(publish(draftId, secondAdmin).andExpect(status().isOk()));
            assertThat(second.get("publishedCommentId").asText()).isEqualTo(commentId);
            assertThat(second.get("alreadyPublished").asBoolean()).isTrue();

            assertThat(commentCount(target.getId())).isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT member_id FROM comment WHERE comment_id = ?",
                                    Long.class,
                                    Long.parseLong(commentId)))
                    .isEqualTo(admin.getId());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT comment_count FROM post WHERE post_id = ?",
                                    Integer.class,
                                    target.getId()))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM search_outbox WHERE source_type = 'COMMENT'"
                                            + " AND source_id = ?",
                                    Integer.class,
                                    Long.parseLong(commentId)))
                    .isPositive();
            assertThat(auditActions(draftId)).containsExactly("SAVE", "APPROVE", "PUBLISH");
        }

        @Test
        @DisplayName("동시에 두 번 게시해도 댓글은 하나다")
        void concurrentPublishCreatesOneComment() throws Exception {
            String draftId = approvedDraft("key-concurrent");
            CustomUserDetails publisher = details(secondAdmin);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Callable<AnswerDraftPublishResponse> task =
                        () -> {
                            start.await();
                            return answerDraftService.publish(draftId, publisher, "127.0.0.1");
                        };
                Future<AnswerDraftPublishResponse> a = pool.submit(task);
                Future<AnswerDraftPublishResponse> b = pool.submit(task);
                start.countDown();
                AnswerDraftPublishResponse ra = a.get(30, TimeUnit.SECONDS);
                AnswerDraftPublishResponse rb = b.get(30, TimeUnit.SECONDS);

                assertThat(ra.publishedCommentId()).isEqualTo(rb.publishedCommentId());
                assertThat(List.of(ra.alreadyPublished(), rb.alreadyPublished()))
                        .containsExactlyInAnyOrder(true, false);
            } finally {
                pool.shutdownNow();
            }
            assertThat(commentCount(target.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("승인 이후 대상 게시글이 수정되면 게시 거절, 거절 기록이 남는다")
        void rejectsWhenTargetEditedAfterApproval() throws Exception {
            String draftId = approvedDraft("key-edited-01");
            jdbc.update(
                    "UPDATE post SET content = '수정된 질문', content_version = content_version + 1"
                            + " WHERE post_id = ?",
                    target.getId());

            publish(draftId, secondAdmin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_008"));
            assertRejected(draftId, "ANSWER_DRAFT_TARGET_CHANGED");
        }

        @Test
        @DisplayName("승인 이후 대상 게시글이 삭제되면 게시 거절")
        void rejectsWhenTargetDeletedAfterApproval() throws Exception {
            String draftId = approvedDraft("key-deleted-01");
            jdbc.update(
                    "UPDATE post SET is_deleted = true, status = 'DELETED',"
                            + " content_version = content_version + 1 WHERE post_id = ?",
                    target.getId());

            publish(draftId, secondAdmin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_007"));
            assertRejected(draftId, "ANSWER_DRAFT_TARGET_UNAVAILABLE");
        }

        @Test
        @DisplayName("근거 원문이 바뀌어 해시가 다르면 게시 거절")
        void rejectsWhenEvidenceChanged() throws Exception {
            String draftId = approvedDraft("key-evidence-1");
            jdbc.update(
                    "UPDATE comment SET content = '내용이 바뀐 근거 댓글', version = version + 1"
                            + " WHERE comment_id = ?",
                    evidenceComment.getId());

            publish(draftId, secondAdmin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_009"));
            assertRejected(draftId, "ANSWER_DRAFT_EVIDENCE_CHANGED");

            JsonNode detail =
                    json(
                            mockMvc.perform(
                                            get(ADMIN_DRAFTS + "/{id}", draftId)
                                                    .with(user(details(admin))))
                                    .andExpect(status().isOk()));
            List<String> statuses = new ArrayList<>();
            detail.get("evidence").forEach(e -> statuses.add(e.get("currentStatus").asText()));
            assertThat(statuses).containsExactly("MATCH", "CHANGED");
        }

        @Test
        @DisplayName("승인자가 관리자에서 내려가면 다른 관리자가 게시해도 거절")
        void rejectsWhenApproverDemoted() throws Exception {
            String draftId = approvedDraft("key-demote-001");
            jdbc.update("UPDATE member SET role = 'ROLE_USER' WHERE member_id = ?", admin.getId());

            publish(draftId, secondAdmin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_010"));
            assertRejected(draftId, "ANSWER_DRAFT_APPROVER_INVALID");
        }

        @Test
        @DisplayName("게시 플래그가 꺼져 있으면 승인된 초안도 게시를 거절한다")
        void refusesWhenFlagOff() throws Exception {
            String draftId = approvedDraft("key-flagoff-01");
            ReflectionTestUtils.setField(publishPolicy, "publishEnabled", false);

            mockMvc.perform(get(ADMIN_DRAFTS).with(user(details(admin))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publishEnabled").value(false));
            publish(draftId, admin)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("DRAFT_006"));
            assertRejected(draftId, "ANSWER_DRAFT_PUBLISH_DISABLED");
        }

        @Test
        @DisplayName("검토 전 초안과 거절된 초안은 게시할 수 없다")
        void requiresApproval() throws Exception {
            String token = createToken(admin, "DRAFT");
            String draftId = saveDraft(token, "key-unapproved");

            publish(draftId, admin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_005"));

            mockMvc.perform(
                            post(ADMIN_DRAFTS + "/{id}/reject", draftId)
                                    .with(csrf())
                                    .with(user(details(admin)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"reason\":\"근거 부족\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.rejectReason").value("근거 부족"));
            publish(draftId, admin)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_005"));
            mockMvc.perform(
                            post(ADMIN_DRAFTS + "/{id}/approve", draftId)
                                    .with(csrf())
                                    .with(user(details(admin))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DRAFT_005"));
            assertThat(commentCount(target.getId())).isZero();
        }

        @Test
        @DisplayName("승인은 승인자와 그때의 대상 content_version을 남긴다")
        void approvalRecordsApproverAndVersion() throws Exception {
            jdbc.update("UPDATE post SET content_version = 7 WHERE post_id = ?", target.getId());
            String draftId = approvedDraft("key-approve-01");

            Map<String, Object> row =
                    jdbc.queryForMap(
                            "SELECT approved_by_member_id, approved_post_content_version, status"
                                    + " FROM answer_draft WHERE public_id = ?",
                            draftId);
            assertThat(row.get("approved_by_member_id")).isEqualTo(admin.getId());
            assertThat(((Number) row.get("approved_post_content_version")).longValue())
                    .isEqualTo(7L);
            assertThat(row.get("status")).isEqualTo("APPROVED");
        }
    }

    private void assertRejected(String draftId, String reasonCode) {
        assertThat(draftStatus(draftId)).isEqualTo("APPROVED");
        assertThat(commentCount(target.getId())).isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT a.reason_code FROM answer_draft_audit a JOIN answer_draft d"
                                        + " ON d.answer_draft_id = a.answer_draft_id"
                                        + " WHERE d.public_id = ? AND a.action = 'PUBLISH_REJECTED'",
                                String.class,
                                draftId))
                .containsExactly(reasonCode);
    }

    private String approvedDraft(String key) throws Exception {
        String token = createToken(admin, "DRAFT");
        String draftId = saveDraft(token, key);
        mockMvc.perform(
                        post(ADMIN_DRAFTS + "/{id}/approve", draftId)
                                .with(csrf())
                                .with(user(details(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        return draftId;
    }

    private ResultActions publish(String draftId, Member publisher) throws Exception {
        return mockMvc.perform(
                post(ADMIN_DRAFTS + "/{id}/publish", draftId)
                        .with(csrf())
                        .with(user(details(publisher))));
    }

    private String saveDraft(String token, String key) throws Exception {
        return json(mockMvc.perform(
                                withToken(post(DRAFTS), token)
                                        .content(write(draftBody(key, "답변 초안 본문"))))
                        .andExpect(status().isCreated()))
                .get("draftId")
                .asText();
    }

    private Map<String, Object> draftBody(String key, String body) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("targetPostPublicId", target.getPublicId());
        request.put("body", body);
        request.put("idempotencyKey", key);
        request.put(
                "evidence",
                List.of(
                        Map.of(
                                "evidenceId",
                                "P:" + evidencePost.getId() + ":1",
                                "chunkHash",
                                SearchTextChunker.chunkPost(EVIDENCE_TITLE, EVIDENCE_BODY)
                                        .get(1)
                                        .hash()),
                        Map.of(
                                "evidenceId",
                                "C:" + evidenceComment.getId() + ":0",
                                "chunkHash",
                                SearchTextChunker.chunkComment(EVIDENCE_COMMENT).get(0).hash())));
        return request;
    }

    private String createToken(Member owner, String... scopes) throws Exception {
        return createTokenResponse(owner, scopes).get("token").asText();
    }

    private JsonNode createTokenResponse(Member owner, String... scopes) throws Exception {
        return json(
                mockMvc.perform(
                                post(TOKENS)
                                        .with(csrf())
                                        .with(user(details(owner)))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                write(
                                                        Map.of(
                                                                "name",
                                                                "mcp-local",
                                                                "scopes",
                                                                List.of(scopes),
                                                                "expiresInDays",
                                                                7))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.apiToken.active").value(true)));
    }

    private String tokenIdOf(String plaintext) {
        return jdbc.queryForObject(
                "SELECT public_id FROM api_token WHERE token_hash = ?",
                String.class,
                ApiTokenService.hash(plaintext));
    }

    /** 토큰 요청은 CSRF 토큰 없이 보낸다. 세션 쿠키도 없다. */
    private static MockHttpServletRequestBuilder withToken(
            MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private String draftStatus(String draftId) {
        return jdbc.queryForObject(
                "SELECT status FROM answer_draft WHERE public_id = ?", String.class, draftId);
    }

    private List<String> auditActions(String draftId) {
        return jdbc.queryForList(
                "SELECT a.action FROM answer_draft_audit a JOIN answer_draft d"
                        + " ON d.answer_draft_id = a.answer_draft_id WHERE d.public_id = ?"
                        + " ORDER BY a.answer_draft_audit_id",
                String.class,
                draftId);
    }

    private int commentCount(Long postId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM comment WHERE post_id = ?", Integer.class, postId);
    }

    private CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void createSeed() {
        String unique = UUID.randomUUID().toString();
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        secondAdmin = saveMember(unique, "b", Role.ROLE_ADMIN);
        user = saveMember(unique, "u", Role.ROLE_USER);
        String code = "ADRF_" + unique.replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        PostCategory category =
                categories.save(PostCategory.builder().code(code).name("초안 테스트").build());
        categoryId = category.getId();

        target = savePost(category, "덕스탠스 각도 질문", "처음 덕스탠스를 하는데 각도를 어떻게 잡나요?", PostStatus.NORMAL);
        hiddenPost = savePost(category, "숨김 질문", "숨김 처리된 질문", PostStatus.HIDDEN);
        evidencePost = savePost(category, EVIDENCE_TITLE, EVIDENCE_BODY, PostStatus.NORMAL);
        evidenceComment =
                comments.save(
                        Comment.create(
                                evidencePost,
                                user,
                                null,
                                EVIDENCE_COMMENT,
                                "127.0.0.1",
                                false,
                                null));
    }

    private Member saveMember(String unique, String suffix, Role role) {
        Member member =
                members.save(
                        Member.builder()
                                .publicId(UUID.randomUUID().toString())
                                .email(unique + "-" + suffix + "@test.invalid")
                                .nickname((suffix + unique).substring(0, 12))
                                .role(role)
                                .build());
        memberIds.add(member.getId());
        return member;
    }

    private Post savePost(PostCategory category, String title, String content, PostStatus status) {
        Post post =
                posts.save(
                        Post.builder()
                                .member(user)
                                .category(category)
                                .title(title)
                                .content(content)
                                .writerIp("127.0.0.1")
                                .isAnonymous(false)
                                .status(status)
                                .build());
        postIds.add(post.getId());
        return post;
    }
}
