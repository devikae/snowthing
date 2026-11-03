package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentUpdateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchOutboxRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.testsupport.SearchDocumentFullTextIndex;

/**
 * outbox 기록·처리·대조·재인덱싱 통합 테스트 (실제 MySQL 8.0).
 *
 * <p>FULLTEXT 검색은 커밋된 행만 보므로 테스트 트랜잭션을 쓰지 않고 각 단계를 커밋한다. 스케줄러는 테스트 설정에서 꺼져 있어 처리기를 직접 부른다. 다른 테스트가
 * 남긴 outbox 행과 섞이지 않게 매 테스트 전후로 검색 관련 테이블을 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchOutboxIntegrationTest {

    private static final String BASE = "/api/v1/admin/search";
    private static final String TITLE = "덕스탠스 세팅 질문";
    private static final String BODY = "스노보드 바인딩 각도는 전경 15도, 후경 -6도에서 시작해 보세요.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private SearchOutboxWriter writer;
    @Autowired private SearchOutboxProcessor processor;
    @Autowired private SearchOutboxRepository outboxRepository;
    @Autowired private SearchConsistencyService consistency;

    private final List<Long> memberIds = new ArrayList<>();
    private String categoryCode;
    private Long categoryId;
    private Member admin;
    private Member user;
    private long postId;
    private String postPublicId;
    private long commentId;
    private long replyId;

    @BeforeEach
    void setUp() {
        SearchDocumentFullTextIndex.ensure(jdbc);
        clearSearchTables();
        categoryCode =
                "OBX_"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 10)
                                .toUpperCase(Locale.ROOT);
        transactions.executeWithoutResult(
                status -> {
                    String unique = UUID.randomUUID().toString();
                    admin = saveMember(unique, "a", Role.ROLE_ADMIN);
                    user = saveMember(unique, "u", Role.ROLE_USER);
                    PostCategory category =
                            categories.save(
                                    PostCategory.builder()
                                            .code(categoryCode)
                                            .name("outbox 테스트")
                                            .build());
                    categoryId = category.getId();
                });

        postPublicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        categoryCode, TITLE, BODY, false, null, List.of()),
                                userDetails(user),
                                "127.0.0.1")
                        .publicId();
        postId = posts.findByPublicId(postPublicId).orElseThrow().getId();
        commentId =
                commentService
                        .createComment(
                                postPublicId,
                                new CommentCreateRequest(
                                        null, "덕스탠스 바인딩 각도는 +15/-15도도 많이 씁니다.", false, null),
                                userDetails(user),
                                "127.0.0.1")
                        .commentId();
        replyId =
                commentService
                        .createComment(
                                postPublicId,
                                new CommentCreateRequest(
                                        commentId, "답글: 바인딩 각도는 체형에 따라 다릅니다.", false, null),
                                userDetails(admin),
                                "127.0.0.1")
                        .commentId();
    }

    @AfterEach
    void tearDown() {
        clearSearchTables();
        transactions.executeWithoutResult(
                status -> {
                    for (Long id :
                            jdbc.queryForList(
                                    "SELECT post_id FROM post WHERE category_id = ?",
                                    Long.class,
                                    categoryId)) {
                        jdbc.update(
                                "DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL",
                                id);
                        jdbc.update("DELETE FROM comment WHERE post_id = ?", id);
                        jdbc.update(
                                "DELETE FROM post_content_image_reference WHERE post_id = ?", id);
                        jdbc.update("DELETE FROM post WHERE post_id = ?", id);
                    }
                    jdbc.update("DELETE FROM post_category WHERE category_id = ?", categoryId);
                    for (Long memberId : memberIds) {
                        jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
                    }
                });
    }

    @Test
    @DisplayName("게시글·댓글 작성·수정·삭제는 같은 트랜잭션에서 outbox 이벤트를 남기고, 롤백되면 이벤트도 남지 않는다")
    void writesOutboxInSameTransaction() {
        assertThat(events(SearchSourceType.POST, postId)).containsExactly("UPSERT:0");
        assertThat(events(SearchSourceType.COMMENT, commentId)).containsExactly("UPSERT:0");

        postService.updatePost(
                postPublicId,
                new PostUpdateRequest(categoryCode, TITLE, BODY + " 수정", null, null),
                userDetails(user));
        commentService.updateComment(
                commentId, new CommentUpdateRequest("수정한 댓글 바인딩", null), userDetails(user));
        commentService.deleteComment(replyId, null, userDetails(admin));
        postService.deletePost(postPublicId, null, userDetails(user));

        assertThat(events(SearchSourceType.POST, postId))
                .containsExactly("UPSERT:0", "UPSERT:1", "DELETE:2");
        assertThat(events(SearchSourceType.COMMENT, commentId))
                .containsExactly("UPSERT:0", "UPSERT:1");
        // 댓글 소프트 삭제는 version을 올리지 않는다. 처리기가 현재 원문을 다시 읽으므로 같은 버전이어도 삭제가 반영된다.
        assertThat(events(SearchSourceType.COMMENT, replyId))
                .containsExactly("UPSERT:0", "DELETE:0");

        Long rolledBack =
                transactions.execute(
                        status -> {
                            String publicId =
                                    postService
                                            .createPost(
                                                    new PostCreateRequest(
                                                            categoryCode,
                                                            "롤백될 글",
                                                            "롤백될 본문",
                                                            false,
                                                            null,
                                                            List.of()),
                                                    userDetails(user),
                                                    "127.0.0.1")
                                            .publicId();
                            Long id = posts.findByPublicId(publicId).orElseThrow().getId();
                            status.setRollbackOnly();
                            return id;
                        });
        assertThat(events(SearchSourceType.POST, rolledBack)).isEmpty();

        assertThatThrownBy(
                        () ->
                                writer.enqueue(
                                        SearchSourceType.POST,
                                        postId,
                                        0,
                                        SearchOutboxWriter.UPSERT))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("처리하면 게시글 구간과 댓글이 색인되고, 같은 이벤트를 두 번 처리해도 결과가 같다")
    void processesAndIsIdempotent() throws Exception {
        processAll();
        List<String> first = documents();
        assertThat(evidenceIds())
                .containsExactlyInAnyOrder(
                        "P:" + postId + ":0",
                        "P:" + postId + ":1",
                        "C:" + commentId + ":0",
                        "C:" + replyId + ":0");
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("0:false");
        assertThat(outboxStatuses()).containsOnly("DONE");
        assertThat(searchEvidenceIds("바인딩 각도")).contains("P:" + postId + ":1");

        enqueue(SearchSourceType.POST, postId, 0);
        enqueue(SearchSourceType.POST, postId, 0);
        enqueue(SearchSourceType.COMMENT, commentId, 0);
        processAll();

        assertThat(documents()).isEqualTo(first);
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("0:false");
        assertThat(outboxStatuses()).containsOnly("DONE");
    }

    @Test
    @DisplayName("새 버전을 반영한 뒤 도착한 옛 버전 이벤트는 색인을 건드리지 않고 완료된다")
    void ignoresOlderVersionAfterNewer() {
        editPost(BODY + " 첫 수정");
        editPost(BODY + " 두 번째 수정");
        processAll();
        List<String> latest = documents();
        LocalDateTime indexedAt = indexedAt("P:" + postId + ":1");
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("2:false");

        enqueue(SearchSourceType.POST, postId, 1);
        enqueue(SearchSourceType.POST, postId, 0);
        processAll();

        assertThat(documents()).isEqualTo(latest);
        assertThat(indexedAt("P:" + postId + ":1")).isEqualTo(indexedAt);
        assertThat(outboxStatuses()).containsOnly("DONE");
    }

    @Test
    @DisplayName("삭제 뒤에 늦게 처리된 수정 이벤트가 문서를 되살리지 않는다")
    void staleUpsertAfterDeleteDoesNotResurrect() {
        processAll();
        postService.deletePost(postPublicId, null, userDetails(user));
        processAll();
        assertThat(evidenceIds()).isEmpty();
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("1:true");
        assertThat(state(SearchSourceType.COMMENT, commentId)).isEqualTo("0:true");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT tombstoned_at FROM search_index_state"
                                        + " WHERE source_type = 'POST' AND source_id = ?",
                                LocalDateTime.class,
                                postId))
                .isNotNull();

        // 옛 버전(0)은 버전 비교로, 같은 버전(1)은 현재 원문을 다시 읽어서 되살리지 않는다.
        enqueue(SearchSourceType.POST, postId, 0);
        enqueue(SearchSourceType.POST, postId, 1);
        enqueue(SearchSourceType.COMMENT, commentId, 0);
        processAll();

        assertThat(evidenceIds()).isEmpty();
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("1:true");
    }

    @Test
    @DisplayName("게시글이 숨김·차단되면 글과 댓글 문서가 색인에서 빠지고, 다시 공개되면 돌아온다")
    void hidingPostRemovesPostAndCommentDocuments() throws Exception {
        processAll();
        assertThat(evidenceIds()).hasSize(4);

        changeStatus(PostStatus.HIDDEN);
        processAll();
        assertThat(evidenceIds()).isEmpty();
        assertThat(state(SearchSourceType.COMMENT, replyId)).isEqualTo("0:true");
        assertThat(searchEvidenceIds("바인딩 각도")).isEmpty();
        getEvidence("C:" + commentId + ":0").andExpect(status().isNotFound());

        changeStatus(PostStatus.NORMAL);
        processAll();
        assertThat(evidenceIds()).hasSize(4);
        assertThat(state(SearchSourceType.COMMENT, replyId)).isEqualTo("0:false");

        changeStatus(PostStatus.BLOCKED);
        processAll();
        assertThat(evidenceIds()).isEmpty();
        assertThat(state(SearchSourceType.POST, postId)).isEqualTo("3:true");
    }

    @Test
    @DisplayName("색인 중 예외가 나면 지수 백오프로 다시 시도하고, 최대 횟수 뒤 FAILED가 되며, 재시도 API로 되살린다")
    void retriesThenFails() throws Exception {
        processAll();
        // 다른 게시글 ID를 가진 같은 evidence_id 행을 심어 둔다(손상된 색인). 처리기가 글 문서를 다시 넣을 때 UNIQUE 위반이 난다.
        jdbc.update(
                "UPDATE search_document SET post_id = ?, source_id = ? WHERE evidence_id = ?",
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                "P:" + postId + ":0");
        enqueue(SearchSourceType.POST, postId, 0);
        long eventId = latestEventId();

        for (int attempt = 1; attempt <= 5; attempt++) {
            LocalDateTime before = LocalDateTime.now();
            processor.processBatch();
            Map<String, Object> row = event(eventId);
            assertThat(((Number) row.get("attempts")).intValue()).isEqualTo(attempt);
            assertThat(row.get("last_error_code")).isEqualTo("DuplicateKeyException");
            if (attempt < 5) {
                assertThat(row.get("status")).isEqualTo("PENDING");
                LocalDateTime dueAt = (LocalDateTime) row.get("due_at");
                long expectedDelayMs = 1000L << (attempt - 1);
                assertThat(dueAt)
                        .isAfterOrEqualTo(before.plusNanos((expectedDelayMs - 50) * 1_000_000));
                jdbc.update(
                        "UPDATE search_outbox SET due_at = ? WHERE event_id = ?", before, eventId);
            } else {
                assertThat(row.get("status")).isEqualTo("FAILED");
            }
        }
        // 실패한 처리의 트랜잭션은 롤백되어 기존 댓글 문서는 그대로 남아 있다.
        assertThat(evidenceIds()).contains("C:" + commentId + ":0");

        asAdmin(get(BASE + "/outbox/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.pendingCount").value(0));

        jdbc.update("DELETE FROM search_document WHERE post_id = ?", Long.MAX_VALUE);
        asAdmin(post(BASE + "/outbox/retry-failed").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retriedCount").value(1));
        assertThat(event(eventId).get("status")).isEqualTo("PENDING");
        assertThat(((Number) event(eventId).get("attempts")).intValue()).isZero();

        processAll();
        assertThat(event(eventId).get("status")).isEqualTo("DONE");
        assertThat(evidenceIds()).hasSize(4);
    }

    @Test
    @DisplayName("이벤트를 가져간 처리기가 죽으면 lease가 끝날 때까지 기다렸다가 다른 처리기가 같은 상태로 반영한다")
    void reprocessesAfterLeaseExpires() {
        processAll();
        List<String> expected = documents();

        jdbc.update("DELETE FROM search_document");
        enqueue(SearchSourceType.POST, postId, 0);
        long eventId = latestEventId();
        // 처리기 A: 이벤트를 가져가 lease를 건 뒤 반영하지 못하고 죽었다.
        transactions.executeWithoutResult(
                status ->
                        outboxRepository.claim(
                                LocalDateTime.now(), LocalDateTime.now().plusMinutes(1), 10));
        assertThat(event(eventId).get("lease_until")).isNotNull();

        // lease가 남아 있는 동안 처리기 B는 가져가지 않는다.
        assertThat(processor.processBatch()).isZero();
        assertThat(event(eventId).get("status")).isEqualTo("PENDING");
        assertThat(evidenceIds()).isEmpty();

        jdbc.update(
                "UPDATE search_outbox SET lease_until = ? WHERE event_id = ?",
                LocalDateTime.now().minusSeconds(1),
                eventId);
        processAll();

        assertThat(event(eventId).get("status")).isEqualTo("DONE");
        assertThat(((Number) event(eventId).get("attempts")).intValue()).isEqualTo(2);
        assertThat(documents()).isEqualTo(expected);
    }

    @Test
    @DisplayName("대조는 손상된 색인(빠진 문서, 숨김 글 문서, 틀린 적용 버전, 상태 없음)을 찾아 outbox로 고친다")
    void reconciliationRepairsCorruptedIndex() throws Exception {
        long hiddenPostId = createPostDirectly("숨김 글 바인딩", PostStatus.HIDDEN);
        processAll();
        List<String> expected = documents();

        jdbc.update("DELETE FROM search_document WHERE evidence_id = ?", "P:" + postId + ":1");
        jdbc.update(
                "INSERT INTO search_document (evidence_id, source_type, source_id, chunk_no,"
                        + " post_id, post_public_id, category_code, source_version, chunk_hash,"
                        + " title, body, source_created_at, source_updated_at, indexed_at)"
                        + " VALUES (?, 'POST', ?, 1, ?, ?, ?, 0, REPEAT('a', 64), NULL,"
                        + " '숨김 글 바인딩 각도', NOW(6), NOW(6), NOW(6))",
                "P:" + hiddenPostId + ":1",
                hiddenPostId,
                hiddenPostId,
                UUID.randomUUID().toString(),
                categoryCode);
        jdbc.update(
                "UPDATE search_index_state SET applied_version = 99"
                        + " WHERE source_type = 'COMMENT' AND source_id = ?",
                commentId);
        jdbc.update(
                "DELETE FROM search_index_state WHERE source_type = 'COMMENT' AND source_id = ?",
                replyId);

        long marker = latestEventId();
        JsonNode result = json(asAdmin(post(BASE + "/reconcile").with(csrf())));
        // 같은 테스트 DB에 다른 테스트가 남긴 원문이 있을 수 있어 건수는 최소값만 보고, 이 테스트 원문의 이벤트를 직접 확인한다.
        assertThat(result.get("postUpserts").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(result.get("postDeletes").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(result.get("commentUpserts").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(eventsAfter(marker, SearchSourceType.POST, postId)).containsExactly("UPSERT:0");
        assertThat(eventsAfter(marker, SearchSourceType.POST, hiddenPostId))
                .containsExactly("DELETE:0");
        assertThat(eventsAfter(marker, SearchSourceType.COMMENT, commentId))
                .containsExactly("UPSERT:99");
        assertThat(eventsAfter(marker, SearchSourceType.COMMENT, replyId))
                .containsExactly("UPSERT:0");

        processAll();
        assertThat(documents()).isEqualTo(expected);
        assertThat(state(SearchSourceType.COMMENT, commentId)).isEqualTo("0:false");
        assertThat(state(SearchSourceType.POST, hiddenPostId)).isEqualTo("0:true");

        JsonNode again = json(asAdmin(post(BASE + "/reconcile").with(csrf())));
        assertThat(again.get("postUpserts").asInt()).isZero();
        assertThat(again.get("postDeletes").asInt()).isZero();
        assertThat(again.get("commentUpserts").asInt()).isZero();
        assertThat(again.get("commentDeletes").asInt()).isZero();
    }

    @Test
    @DisplayName("재인덱싱은 기존 문서를 지우지 않고 배치로 outbox에 넣으며, 중간에 멈추면 체크포인트부터 이어서 한다")
    void reindexResumesFromCheckpoint() throws Exception {
        long secondPostId = createPostDirectly("두 번째 바인딩 글", PostStatus.NORMAL);
        processAll();
        List<String> expected = documents();
        long marker = latestEventId();
        Integer visiblePosts =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post p JOIN post_category pc"
                                + " ON pc.category_id = p.category_id"
                                + " WHERE p.status = 'NORMAL' AND p.is_deleted = false"
                                + " AND pc.code <> 'MARKET'",
                        Integer.class);

        Object originalBatchSize = ReflectionTestUtils.getField(consistency, "reindexBatchSize");
        ReflectionTestUtils.setField(consistency, "reindexBatchSize", 1);
        try {
            // 첫 배치만 넣고 멈춘 상황
            Boolean resumed = transactions.execute(status -> consistency.startReindex());
            assertThat(resumed).isFalse();
            Boolean more = transactions.execute(status -> consistency.runReindexBatch());
            assertThat(more).isTrue();
            assertThat(newPostEvents(marker)).hasSize(1);

            // 재인덱싱 중에도 기존 문서로 검색이 된다.
            assertThat(searchEvidenceIds("바인딩 각도")).contains("P:" + postId + ":1");

            JsonNode result = json(asAdmin(post(BASE + "/reindex").with(csrf())));
            assertThat(result.get("status").asText()).isEqualTo("DONE");
            assertThat(result.get("resumed").asBoolean()).isTrue();
            assertThat(result.get("enqueuedCount").asInt()).isEqualTo(visiblePosts);
        } finally {
            ReflectionTestUtils.setField(consistency, "reindexBatchSize", originalBatchSize);
        }

        List<Long> enqueued = newPostEvents(marker);
        assertThat(enqueued).hasSize(visiblePosts).doesNotHaveDuplicates();
        assertThat(enqueued).contains(postId, secondPostId);

        processAll();
        assertThat(documents()).isEqualTo(expected);

        // DONE 뒤에 다시 부르면 처음부터 새로 시작한다.
        JsonNode again = json(asAdmin(post(BASE + "/reindex").with(csrf())));
        assertThat(again.get("resumed").asBoolean()).isFalse();
        assertThat(again.get("status").asText()).isEqualTo("DONE");
    }

    @Test
    @DisplayName("정합성 관리 API는 관리자만 쓸 수 있다")
    void requiresAdmin() throws Exception {
        mockMvc.perform(get(BASE + "/outbox/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/reconcile").with(csrf()).with(user(userDetails(user))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/reindex").with(csrf()).with(user(userDetails(user))))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post(BASE + "/outbox/retry-failed")
                                .with(csrf())
                                .with(user(userDetails(user))))
                .andExpect(status().isForbidden());
        asAdmin(get(BASE + "/outbox/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingCount").value(3))
                .andExpect(jsonPath("$.oldestPendingAgeSeconds").isNumber());
    }

    private void processAll() {
        for (int i = 0; i < 20 && processor.processBatch() > 0; i++) {
            // 남은 이벤트가 없을 때까지
        }
    }

    private void editPost(String body) {
        postService.updatePost(
                postPublicId,
                new PostUpdateRequest(categoryCode, TITLE, body, null, null),
                userDetails(user));
    }

    /** 운영의 상태 변경 경로(MarketListingService.updateModerationStatus)와 같이 엔티티를 바꾸고 같은 트랜잭션에서 이벤트를 쓴다. */
    private void changeStatus(PostStatus status) {
        transactions.executeWithoutResult(
                tx -> {
                    Post post = posts.findById(postId).orElseThrow();
                    post.changeStatus(status);
                    writer.postChanged(post);
                });
    }

    private long createPostDirectly(String title, PostStatus status) {
        return transactions.execute(
                tx -> {
                    Post post =
                            posts.save(
                                    Post.builder()
                                            .member(user)
                                            .category(categories.findById(categoryId).orElseThrow())
                                            .title(title)
                                            .content(title + " 바인딩 각도 본문")
                                            .writerIp("127.0.0.1")
                                            .status(status)
                                            .build());
                    writer.postChanged(post);
                    return post.getId();
                });
    }

    private void enqueue(SearchSourceType type, long sourceId, long version) {
        transactions.executeWithoutResult(
                status -> writer.enqueue(type, sourceId, version, SearchOutboxWriter.UPSERT));
    }

    private List<String> events(SearchSourceType type, long sourceId) {
        return jdbc.queryForList(
                "SELECT CONCAT(operation, ':', source_version) FROM search_outbox"
                        + " WHERE source_type = ? AND source_id = ? ORDER BY event_id",
                String.class,
                type.name(),
                sourceId);
    }

    private List<String> eventsAfter(long afterEventId, SearchSourceType type, long sourceId) {
        return jdbc.queryForList(
                "SELECT CONCAT(operation, ':', source_version) FROM search_outbox"
                        + " WHERE event_id > ? AND source_type = ? AND source_id = ?"
                        + " ORDER BY event_id",
                String.class,
                afterEventId,
                type.name(),
                sourceId);
    }

    private List<Long> newPostEvents(long afterEventId) {
        return jdbc.queryForList(
                "SELECT source_id FROM search_outbox WHERE event_id > ? AND source_type = 'POST'"
                        + " ORDER BY event_id",
                Long.class,
                afterEventId);
    }

    private long latestEventId() {
        Long id =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(event_id), 0) FROM search_outbox", Long.class);
        return id == null ? 0 : id;
    }

    private Map<String, Object> event(long eventId) {
        return jdbc.queryForMap(
                "SELECT status, attempts, due_at, lease_until, last_error_code FROM search_outbox"
                        + " WHERE event_id = ?",
                eventId);
    }

    private List<String> outboxStatuses() {
        return jdbc.queryForList("SELECT DISTINCT status FROM search_outbox", String.class);
    }

    private String state(SearchSourceType type, long sourceId) {
        return jdbc.queryForObject(
                "SELECT CONCAT(applied_version, ':', IF(tombstoned, 'true', 'false'))"
                        + " FROM search_index_state WHERE source_type = ? AND source_id = ?",
                String.class,
                type.name(),
                sourceId);
    }

    /** 이 테스트 카테고리 글의 문서를 (근거 ID, 버전, 해시, 본문) 순서대로. indexed_at은 빼서 다시 만든 문서와 비교할 수 있게 한다. */
    private List<String> documents() {
        return jdbc.queryForList(
                "SELECT CONCAT_WS('|', evidence_id, source_version, chunk_hash, COALESCE(title, ''),"
                        + " body, COALESCE(parent_comment_id, ''))"
                        + " FROM search_document WHERE category_code = ? ORDER BY evidence_id",
                String.class,
                categoryCode);
    }

    private List<String> evidenceIds() {
        return jdbc.queryForList(
                "SELECT evidence_id FROM search_document WHERE category_code = ?"
                        + " ORDER BY evidence_id",
                String.class,
                categoryCode);
    }

    private LocalDateTime indexedAt(String evidenceId) {
        return jdbc.queryForObject(
                "SELECT indexed_at FROM search_document WHERE evidence_id = ?",
                LocalDateTime.class,
                evidenceId);
    }

    private List<String> searchEvidenceIds(String q) throws Exception {
        JsonNode items =
                json(asAdmin(
                                get(BASE + "/evidence")
                                        .param("q", q)
                                        .param("size", "20")
                                        .param("categoryCode", categoryCode)))
                        .get("items");
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("evidenceId").asText()));
        return ids;
    }

    private ResultActions getEvidence(String evidenceId) throws Exception {
        return asAdmin(get(BASE + "/evidence/{id}", evidenceId));
    }

    private ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.with(user(userDetails(admin))));
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8));
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

    private static CustomUserDetails userDetails(Member member) {
        return new CustomUserDetails(member);
    }

    private void clearSearchTables() {
        jdbc.update("DELETE FROM search_outbox");
        jdbc.update("DELETE FROM search_index_state");
        jdbc.update("DELETE FROM search_document");
        jdbc.update("DELETE FROM search_reindex_checkpoint");
    }
}
