package com.ikae.snowthing.domain.search.controller;

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
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.ikae.snowthing.testsupport.SearchDocumentFullTextIndex;

/**
 * 근거 검색 API 통합 테스트.
 *
 * <p>InnoDB FULLTEXT 인덱스는 커밋된 행만 검색하므로 테스트 트랜잭션으로 감싸지 않는다(@Transactional 없음). 각 단계는
 * TransactionTemplate으로 커밋하고, 끝나면 만든 행을 직접 지운다. 다른 테스트 데이터와 섞이지 않도록 검색은 이 테스트가 만든 카테고리로 거른다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SearchAdminIntegrationTest {

    private static final String BASE = "/api/v1/admin/search";
    private static final String KOREAN_BODY =
            "스노보드 바인딩 각도는 전경 15도, 후경 -6도에서 시작해 보세요.\n\n덕스탠스가 처음이면 무릎 부담을 먼저 확인합니다.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();
    private Long categoryId;
    private String categoryCode;
    private Member admin;
    private Member user;
    private Seed seed;

    @BeforeEach
    void setUp() {
        SearchDocumentFullTextIndex.ensure(jdbc);
        categoryCode =
                "SRCH_"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 10)
                                .toUpperCase(Locale.ROOT);
        seed = transactions.execute(status -> createSeed());
    }

    @AfterEach
    void tearDown() {
        transactions.executeWithoutResult(
                status -> {
                    jdbc.update("DELETE FROM search_document");
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

    @Test
    @DisplayName("비로그인은 401, 일반 회원은 403으로 막힌다")
    void requiresAdmin() throws Exception {
        mockMvc.perform(post(BASE + "/rebuild").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/evidence").param("q", "바인딩"))
                .andExpect(status().isUnauthorized());

        CustomUserDetails member = new CustomUserDetails(user);
        mockMvc.perform(post(BASE + "/rebuild").with(csrf()).with(user(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/evidence").param("q", "바인딩").with(user(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get(BASE + "/evidence/{id}", "P:" + seed.visiblePostId() + ":1")
                                .with(user(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("재구축은 공개 게시글과 삭제되지 않은 댓글만 색인한다")
    void rebuildIndexesOnlyVisibleContent() throws Exception {
        JsonNode result = json(rebuild());

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM search_document", Integer.class);
        assertThat(result.get("documentCount").asInt()).isEqualTo(total);
        assertThat(result.get("documentCount").asInt())
                .isEqualTo(
                        result.get("postChunkCount").asInt() + result.get("commentCount").asInt());

        assertThat(evidenceIdsOfPost(seed.visiblePostId()))
                .containsExactlyInAnyOrder(
                        "P:" + seed.visiblePostId() + ":0",
                        "P:" + seed.visiblePostId() + ":1",
                        "C:" + seed.visibleCommentId() + ":0",
                        "C:" + seed.replyId() + ":0");
        assertThat(evidenceIdsOfPost(seed.hiddenPostId())).isEmpty();
        assertThat(evidenceIdsOfPost(seed.deletedPostId())).isEmpty();
        assertThat(evidenceIdsOfPost(seed.marketPostId())).isEmpty();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT parent_comment_id FROM search_document WHERE evidence_id = ?",
                                Long.class,
                                "C:" + seed.replyId() + ":0"))
                .isEqualTo(seed.visibleCommentId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM search_document WHERE evidence_id = ?",
                                Integer.class,
                                "C:" + seed.deletedCommentId() + ":0"))
                .isZero();
    }

    @Test
    @DisplayName("한국어 검색어가 ngram으로 게시글 구간과 댓글을 찾고, 점수·내부 ID·회원 정보는 반환하지 않는다")
    void searchesKoreanWithNgram() throws Exception {
        rebuild();
        String postEvidence = "P:" + seed.visiblePostId() + ":1";

        JsonNode body =
                json(
                        mockMvc.perform(
                                        get(BASE + "/evidence")
                                                .param("q", "  바인딩 각도 ")
                                                .param("categoryCode", categoryCode)
                                                .with(user(new CustomUserDetails(admin))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.query").value("바인딩 각도"))
                                .andExpect(jsonPath("$.size").value(10)));

        JsonNode items = body.get("items");
        // 공개 게시글 본문 구간 1개 + 댓글 2개. 제목 구간·숨김·삭제·MARKET 글과 삭제 댓글은 나오지 않는다.
        assertThat(items).hasSize(3);
        JsonNode first = findItem(items, postEvidence);
        assertThat(first).isNotNull();
        assertThat(first.get("sourceType").asText()).isEqualTo("POST");
        assertThat(first.get("postPublicId").asText()).isEqualTo(seed.visiblePostPublicId());
        assertThat(first.get("commentId").isNull()).isTrue();
        assertThat(first.get("chunkNo").asInt()).isEqualTo(1);
        assertThat(first.get("sourceVersion").asLong()).isZero();
        assertThat(first.get("url").asText()).isEqualTo("/posts/" + seed.visiblePostPublicId());
        assertThat(first.get("title").asText()).isEqualTo("덕스탠스 세팅 질문");
        assertThat(first.get("excerpt").asText()).startsWith("스노보드 바인딩 각도는");
        assertThat(first.get("excerptTruncated").asBoolean()).isFalse();
        assertThat(first.get("chunkHash").asText())
                .isEqualTo(SearchTextChunker.chunkPost("덕스탠스 세팅 질문", KOREAN_BODY).get(1).hash());
        assertThat(first.has("score")).isFalse();
        assertThat(first.has("postId")).isFalse();
        assertThat(first.has("writer")).isFalse();
        assertThat(first.has("sourceId")).isFalse();

        JsonNode commentItem = findItem(items, "C:" + seed.visibleCommentId() + ":0");
        assertThat(commentItem).isNotNull();
        assertThat(commentItem.get("commentId").asText())
                .isEqualTo(String.valueOf(seed.visibleCommentId()));
        assertThat(commentItem.get("url").asText())
                .isEqualTo("/posts/" + seed.visiblePostPublicId());

        JsonNode replyItem = findItem(items, "C:" + seed.replyId() + ":0");
        assertThat(replyItem.get("parentCommentId").asText())
                .isEqualTo(String.valueOf(seed.visibleCommentId()));
    }

    @Test
    @DisplayName("색인 뒤에 숨김 처리된 게시글과 삭제된 댓글은 재구축 없이도 검색 결과에서 빠진다")
    void filtersAtQueryTime() throws Exception {
        rebuild();
        assertThat(searchEvidenceIds("바인딩 각도"))
                .contains(
                        "P:" + seed.visiblePostId() + ":1", "C:" + seed.visibleCommentId() + ":0");

        transactions.executeWithoutResult(
                status -> {
                    Comment comment = comments.findById(seed.replyId()).orElseThrow();
                    comment.softDelete();
                });
        assertThat(searchEvidenceIds("바인딩 각도"))
                .contains("P:" + seed.visiblePostId() + ":1")
                .doesNotContain("C:" + seed.replyId() + ":0");

        transactions.executeWithoutResult(
                status ->
                        posts.findById(seed.visiblePostId())
                                .orElseThrow()
                                .changeStatus(PostStatus.HIDDEN));
        assertThat(searchEvidenceIds("바인딩 각도")).isEmpty();
        assertThat(evidenceIdsOfPost(seed.visiblePostId())).isNotEmpty();
    }

    @Test
    @DisplayName("근거 조회는 원문을 다시 읽어 현재 구간과 해시를 돌려주고, 삭제 뒤에는 404")
    void getEvidenceRechecksSource() throws Exception {
        rebuild();
        String evidenceId = "P:" + seed.visiblePostId() + ":1";
        String hash = SearchTextChunker.chunkPost("덕스탠스 세팅 질문", KOREAN_BODY).get(1).hash();

        getEvidence(evidenceId, hash)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidenceId").value(evidenceId))
                .andExpect(jsonPath("$.chunkHash").value(hash))
                .andExpect(jsonPath("$.indexedChunkHash").value(hash))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.text").value(KOREAN_BODY))
                .andExpect(jsonPath("$.textTruncated").value(false))
                .andExpect(jsonPath("$.url").value("/posts/" + seed.visiblePostPublicId()))
                .andExpect(jsonPath("$.indexedAt").isNotEmpty());

        getEvidence("C:" + seed.visibleCommentId() + ":0", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(String.valueOf(seed.visibleCommentId())))
                .andExpect(jsonPath("$.text").value("덕스탠스 바인딩 각도는 +15/-15도도 많이 씁니다."));

        transactions.executeWithoutResult(
                status -> posts.findById(seed.visiblePostId()).orElseThrow().softDelete());

        getEvidence(evidenceId, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_001"));
        // 게시글이 삭제되면 그 글의 댓글 근거도 볼 수 없다.
        getEvidence("C:" + seed.visibleCommentId() + ":0", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_001"));
    }

    @Test
    @DisplayName("없는 구간·숨김 글·MARKET 글·삭제 댓글은 404, 형식 오류는 400")
    void getEvidenceNotFoundAndInvalid() throws Exception {
        rebuild();
        getEvidence("P:" + seed.visiblePostId() + ":99", null).andExpect(status().isNotFound());
        getEvidence("P:" + seed.hiddenPostId() + ":0", null).andExpect(status().isNotFound());
        getEvidence("P:" + seed.marketPostId() + ":0", null).andExpect(status().isNotFound());
        getEvidence("C:" + seed.deletedCommentId() + ":0", null).andExpect(status().isNotFound());

        getEvidence("X:1:0", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
        getEvidence("P:" + seed.visiblePostId() + ":0", "not-a-hash")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        mockMvc.perform(get(BASE + "/evidence").param("q", "가").with(user(adminDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
        mockMvc.perform(
                        get(BASE + "/evidence")
                                .param("q", "바인딩")
                                .param("size", "21")
                                .with(user(adminDetails)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get(BASE + "/evidence")
                                .param("q", "바인딩")
                                .param("size", "abc")
                                .with(user(adminDetails)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/evidence").with(user(adminDetails)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("재구축 없이 게시글을 수정하면 stale=true가 되고, 이전 해시를 expectedHash로 보내면 409")
    void staleAndHashMismatchAfterEdit() throws Exception {
        rebuild();
        String evidenceId = "P:" + seed.visiblePostId() + ":1";
        String oldHash = SearchTextChunker.chunkPost("덕스탠스 세팅 질문", KOREAN_BODY).get(1).hash();
        String newBody = "바인딩 각도를 전경 18도로 바꿨습니다.";

        transactions.executeWithoutResult(
                status -> {
                    Post post = posts.findById(seed.visiblePostId()).orElseThrow();
                    post.update(post.getTitle(), newBody, post.getCategory());
                });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content_version FROM post WHERE post_id = ?",
                                Long.class,
                                seed.visiblePostId()))
                .isEqualTo(1L);

        String newHash = SearchTextChunker.chunkPost("덕스탠스 세팅 질문", newBody).get(1).hash();
        getEvidence(evidenceId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.sourceVersion").value(1))
                .andExpect(jsonPath("$.chunkHash").value(newHash))
                .andExpect(jsonPath("$.indexedChunkHash").value(oldHash))
                .andExpect(jsonPath("$.text").value(newBody));

        getEvidence(evidenceId, oldHash)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_002"));
        getEvidence(evidenceId, newHash.toUpperCase(Locale.ROOT)).andExpect(status().isOk());

        // 검색 결과는 재구축 전까지 색인 시점의 해시와 버전을 그대로 보여 준다.
        JsonNode item = findItem(searchItems("바인딩 각도"), evidenceId);
        assertThat(item.get("chunkHash").asText()).isEqualTo(oldHash);
        assertThat(item.get("sourceVersion").asLong()).isZero();

        rebuild();
        getEvidence(evidenceId, newHash).andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    @DisplayName("평가용 조건: createdBefore보다 늦게 쓴 문서와 excludePostPublicId 글타래는 결과에서 빠진다")
    void evaluationFilters() throws Exception {
        transactions.executeWithoutResult(
                status -> {
                    jdbc.update(
                            "UPDATE post SET created_at = '2026-10-01 10:00:00' WHERE post_id = ?",
                            seed.visiblePostId());
                    jdbc.update(
                            "UPDATE comment SET created_at = '2026-10-02 10:00:00'"
                                    + " WHERE comment_id = ?",
                            seed.visibleCommentId());
                    jdbc.update(
                            "UPDATE comment SET created_at = '2026-10-03 10:00:00'"
                                    + " WHERE comment_id = ?",
                            seed.replyId());
                });
        rebuild();
        String post = "P:" + seed.visiblePostId() + ":1";
        String comment = "C:" + seed.visibleCommentId() + ":0";
        String reply = "C:" + seed.replyId() + ":0";

        assertThat(searchEvidenceIds("바인딩 각도", "2026-10-02T10:00:01", null))
                .containsExactlyInAnyOrder(post, comment);
        // 경계 시각과 같으면 제외한다(질문보다 먼저 쓴 글만 근거로 본다).
        assertThat(searchEvidenceIds("바인딩 각도", "2026-10-02T10:00:00", null)).containsExactly(post);
        assertThat(searchEvidenceIds("바인딩 각도", null, null)).contains(reply);
        assertThat(
                        searchEvidenceIds(
                                "바인딩 각도",
                                null,
                                seed.visiblePostPublicId().toUpperCase(Locale.ROOT)))
                .isEmpty();

        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        mockMvc.perform(
                        get(BASE + "/evidence")
                                .param("q", "바인딩")
                                .param("createdBefore", "2026-10-02")
                                .with(user(adminDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
        mockMvc.perform(
                        get(BASE + "/evidence")
                                .param("q", "바인딩")
                                .param("excludePostPublicId", "not-a-uuid")
                                .with(user(adminDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_001"));
    }

    private List<String> searchEvidenceIds(
            String q, String createdBefore, String excludePostPublicId) throws Exception {
        var request =
                get(BASE + "/evidence")
                        .param("q", q)
                        .param("size", "20")
                        .param("categoryCode", categoryCode)
                        .with(user(new CustomUserDetails(admin)));
        if (createdBefore != null) {
            request.param("createdBefore", createdBefore);
        }
        if (excludePostPublicId != null) {
            request.param("excludePostPublicId", excludePostPublicId);
        }
        List<String> ids = new ArrayList<>();
        json(mockMvc.perform(request).andExpect(status().isOk()))
                .get("items")
                .forEach(item -> ids.add(item.get("evidenceId").asText()));
        return ids;
    }

    @Test
    @DisplayName("게시글을 삭제하면 DB의 content_version이 오른다")
    void contentVersionIncrementsOnDelete() {
        transactions.executeWithoutResult(
                status -> posts.findById(seed.visiblePostId()).orElseThrow().softDelete());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT content_version FROM post WHERE post_id = ?",
                                Long.class,
                                seed.visiblePostId()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("저장소 delete(@SQLDelete) 경로도 내용 버전을 올린다")
    void sqlDeleteIncrementsContentVersion() {
        Long before =
                jdbc.queryForObject(
                        "SELECT content_version FROM post WHERE post_id = ?",
                        Long.class,
                        seed.visiblePostId());

        transactions.executeWithoutResult(
                status -> posts.delete(posts.findById(seed.visiblePostId()).orElseThrow()));

        Long after =
                jdbc.queryForObject(
                        "SELECT content_version FROM post WHERE post_id = ?",
                        Long.class,
                        seed.visiblePostId());
        assertThat(after).isEqualTo(before + 1);
    }

    private Seed createSeed() {
        String unique = UUID.randomUUID().toString();
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        user = saveMember(unique, "u", Role.ROLE_USER);

        PostCategory category =
                categories.save(PostCategory.builder().code(categoryCode).name("검색 테스트").build());
        categoryId = category.getId();
        PostCategory market =
                categories
                        .findByCode("MARKET")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("MARKET")
                                                        .name("Market")
                                                        .build()));

        Post visible = savePost(category, "덕스탠스 세팅 질문", KOREAN_BODY, PostStatus.NORMAL);
        Post hidden = savePost(category, "숨김 글 바인딩 각도", "숨김 처리된 바인딩 각도 글", PostStatus.HIDDEN);
        Post deleted = savePost(category, "삭제 글 바인딩 각도", "삭제된 바인딩 각도 글", PostStatus.NORMAL);
        deleted.softDelete();
        Post market1 = savePost(market, "장터 바인딩 판매", "바인딩 각도 조절 가능한 바인딩 판매", PostStatus.NORMAL);

        Comment visibleComment =
                comments.save(
                        Comment.create(
                                visible,
                                user,
                                null,
                                "덕스탠스 바인딩 각도는 +15/-15도도 많이 씁니다.",
                                "127.0.0.1",
                                false,
                                null));
        Comment reply =
                comments.save(
                        Comment.create(
                                visible,
                                admin,
                                visibleComment,
                                "답글: 바인딩 각도는 체형에 따라 다릅니다.",
                                "127.0.0.1",
                                false,
                                null));
        Comment deletedComment =
                comments.save(
                        Comment.create(
                                visible, user, null, "삭제될 바인딩 각도 댓글", "127.0.0.1", false, null));
        deletedComment.softDelete();
        comments.save(
                Comment.create(hidden, user, null, "숨김 글의 바인딩 각도 댓글", "127.0.0.1", false, null));

        return new Seed(
                visible.getId(),
                visible.getPublicId(),
                hidden.getId(),
                deleted.getId(),
                market1.getId(),
                visibleComment.getId(),
                reply.getId(),
                deletedComment.getId());
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

    private ResultActions rebuild() throws Exception {
        return mockMvc.perform(
                        post(BASE + "/rebuild")
                                .with(csrf())
                                .with(user(new CustomUserDetails(admin))))
                .andExpect(status().isOk());
    }

    private ResultActions getEvidence(String evidenceId, String expectedHash) throws Exception {
        var request =
                get(BASE + "/evidence/{id}", evidenceId).with(user(new CustomUserDetails(admin)));
        if (expectedHash != null) {
            request.param("expectedHash", expectedHash);
        }
        return mockMvc.perform(request);
    }

    private JsonNode searchItems(String q) throws Exception {
        return json(mockMvc.perform(
                                get(BASE + "/evidence")
                                        .param("q", q)
                                        .param("size", "20")
                                        .param("categoryCode", categoryCode)
                                        .with(user(new CustomUserDetails(admin))))
                        .andExpect(status().isOk()))
                .get("items");
    }

    private List<String> searchEvidenceIds(String q) throws Exception {
        List<String> ids = new ArrayList<>();
        searchItems(q).forEach(item -> ids.add(item.get("evidenceId").asText()));
        return ids;
    }

    private List<String> evidenceIdsOfPost(long postId) {
        return jdbc.queryForList(
                "SELECT evidence_id FROM search_document WHERE post_id = ?", String.class, postId);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static JsonNode findItem(JsonNode items, String evidenceId) {
        for (JsonNode item : items) {
            if (evidenceId.equals(item.get("evidenceId").asText())) {
                return item;
            }
        }
        return null;
    }

    private record Seed(
            long visiblePostId,
            String visiblePostPublicId,
            long hiddenPostId,
            long deletedPostId,
            long marketPostId,
            long visibleCommentId,
            long replyId,
            long deletedCommentId) {}
}
