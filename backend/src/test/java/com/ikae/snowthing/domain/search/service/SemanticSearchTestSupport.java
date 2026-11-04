package com.ikae.snowthing.domain.search.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.testsupport.CountingEmbeddingProvider;
import com.ikae.snowthing.testsupport.SearchDocumentFullTextIndex;

/**
 * 의미 검색 통합 테스트 공통 준비. 실제 MySQL 8.0, 테스트 트랜잭션 없음(FULLTEXT는 커밋된 행만 본다). 게시글과 댓글은 운영과 같은 서비스로 만들어
 * outbox 이벤트를 남기고 처리기를 직접 부른다. 매 테스트 전후로 검색 관련 테이블을 비우므로 색인에는 이 테스트의 문서만 있다.
 */
abstract class SemanticSearchTestSupport {

    static final String BASE = "/api/v1/admin/search";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MemberRepository members;
    @Autowired PostCategoryRepository categories;
    @Autowired PostRepository posts;
    @Autowired PostService postService;
    @Autowired CommentService commentService;
    @Autowired SearchOutboxProcessor processor;
    @Autowired CountingEmbeddingProvider provider;
    @Autowired SearchEmbeddingIndexer indexer;

    final List<Long> memberIds = new ArrayList<>();
    final List<Long> postIds = new ArrayList<>();
    String categoryCode;
    Long categoryId;
    Member admin;
    Member user;

    @BeforeEach
    void prepareBase() {
        SearchDocumentFullTextIndex.ensure(jdbc);
        clearSearchTables();
        provider.reset();
        resumeEmbeddingRefresh();
        categoryCode =
                "SEM_"
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
                                            .name("의미 검색 테스트")
                                            .build());
                    categoryId = category.getId();
                });
    }

    @AfterEach
    void cleanUpBase() {
        clearSearchTables();
        transactions.executeWithoutResult(
                status -> {
                    for (Long id : postIds) {
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

    Created createPost(String title, String body) {
        String publicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        categoryCode, title, body, false, null, List.of()),
                                details(user),
                                "127.0.0.1")
                        .publicId();
        long id = posts.findByPublicId(publicId).orElseThrow().getId();
        postIds.add(id);
        return new Created(id, publicId);
    }

    void editPost(Created post, String title, String body) {
        postService.updatePost(
                post.publicId(),
                new PostUpdateRequest(categoryCode, title, body, null, null),
                details(user));
    }

    long createComment(Created post, Long parentId, String content) {
        return commentService
                .createComment(
                        post.publicId(),
                        new CommentCreateRequest(parentId, content, false, null),
                        details(user),
                        "127.0.0.1")
                .commentId();
    }

    /** 제공자 실패 뒤의 쉬는 시간과 유휴 조회 간격을 끝낸다. 다른 테스트로 상태가 넘어가지 않게 매 테스트 전에도 부른다. */
    void resumeEmbeddingRefresh() {
        ReflectionTestUtils.setField(indexer, "pausedUntil", null);
        ReflectionTestUtils.setField(indexer, "consecutiveFailures", 0);
        ReflectionTestUtils.setField(indexer, "nextIdleCheckAt", null);
    }

    void processAll() {
        for (int i = 0; i < 20 && processor.processBatch() > 0; i++) {
            // 남은 이벤트가 없을 때까지
        }
    }

    List<String> embeddedEvidenceIds() {
        return jdbc.queryForList(
                "SELECT evidence_id FROM search_embedding ORDER BY evidence_id", String.class);
    }

    List<String> documentEvidenceIds() {
        return jdbc.queryForList(
                "SELECT evidence_id FROM search_document ORDER BY evidence_id", String.class);
    }

    String embeddingHash(String evidenceId) {
        return jdbc
                .queryForList(
                        "SELECT content_hash FROM search_embedding WHERE evidence_id = ?",
                        String.class,
                        evidenceId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    String documentHash(String evidenceId) {
        return jdbc.queryForObject(
                "SELECT chunk_hash FROM search_document WHERE evidence_id = ?",
                String.class,
                evidenceId);
    }

    ResultActions search(String q, String mode, String... extra) throws Exception {
        MockHttpServletRequestBuilder request =
                get(BASE + "/evidence").param("q", q).param("size", "20");
        if (mode != null) {
            request.param("mode", mode);
        }
        for (int i = 0; i + 1 < extra.length; i += 2) {
            request.param(extra[i], extra[i + 1]);
        }
        return mockMvc.perform(request.with(user(details(admin))));
    }

    JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    static List<String> evidenceIds(JsonNode body) {
        List<String> ids = new ArrayList<>();
        body.get("items").forEach(item -> ids.add(item.get("evidenceId").asText()));
        return ids;
    }

    static CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
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

    void clearSearchTables() {
        jdbc.update("DELETE FROM search_outbox");
        jdbc.update("DELETE FROM search_index_state");
        jdbc.update("DELETE FROM search_document");
        jdbc.update("DELETE FROM search_embedding");
        jdbc.update("DELETE FROM search_reindex_checkpoint");
    }

    record Created(long id, String publicId) {}
}
