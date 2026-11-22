package com.ikae.snowthing.domain.moderation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.moderation.service.ModerationProcessor;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;

/**
 * 자동 검열 통합 테스트 공통 도구 (IKA-80). 실제 MySQL, 보안 설정, 로그인 세션으로 글을 쓰고 처리기를 직접 부른다(테스트는 스케줄러를 끈다).
 *
 * <p>커밋 뒤 상태를 보려고 테스트 트랜잭션으로 감싸지 않는다. 만든 행은 tearDown에서 지운다.
 */
abstract class ModerationTestSupport {

    static final String PASSWORD = "Password123!";
    static final String POSTS = "/api/v1/posts";
    static final String WRITER_IP = "203.0.113.45";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MemberRepository members;
    @Autowired PostCategoryRepository categories;
    @Autowired ModerationProcessor processor;

    final List<Long> memberIds = new ArrayList<>();
    final String marker = "mod-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanUp() {
        List<Long> postIds =
                jdbc.queryForList(
                        "SELECT post_id FROM post WHERE title LIKE ?", Long.class, marker + "%");
        for (Long postId : postIds) {
            jdbc.update(
                    "DELETE FROM content_moderation WHERE target_type = 'COMMENT' AND target_id IN"
                            + " (SELECT comment_id FROM comment WHERE post_id = ?)",
                    postId);
            jdbc.update(
                    "DELETE FROM search_outbox WHERE source_type = 'COMMENT' AND source_id IN"
                            + " (SELECT comment_id FROM comment WHERE post_id = ?)",
                    postId);
            jdbc.update(
                    "DELETE FROM content_moderation WHERE target_type IN ('POST', 'MARKET',"
                            + " 'CARPOOL') AND target_id = ?",
                    postId);
            jdbc.update(
                    "DELETE FROM search_outbox WHERE source_type = 'POST' AND source_id = ?",
                    postId);
            jdbc.update("DELETE FROM content_filter_hold WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM notification WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ? AND parent_id IS NOT NULL", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", memberId);
            jdbc.update("DELETE FROM content_moderation WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    /** 남은 줄이 없을 때까지 처리한다. 앞선 테스트가 남긴 줄도 함께 비운다. */
    void drain() {
        for (int i = 0; i < 100 && processor.processBatch() > 0; i++) {
            // 다음 묶음
        }
    }

    void category(String code, String name) {
        categories
                .findByCode(code)
                .orElseGet(
                        () ->
                                categories.save(
                                        PostCategory.builder().name(name).code(code).build()));
    }

    Member member(String prefix, Role role) {
        return member(prefix, role, "@snowthing.com");
    }

    Member member(String prefix, Role role, String emailDomain) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                members.save(
                        Member.builder()
                                .email(prefix + "-" + unique + emailDomain)
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname(prefix + unique)
                                .role(role)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(saved.getId());
        return saved;
    }

    MockHttpSession login(Member member) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                member.getEmail(),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 자유게시판 글을 쓰고 글 번호(post_id)를 돌려준다. 제목 앞에 marker를 붙인다. */
    long writePost(MockHttpSession session, String title, String content) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(POSTS)
                                        .session(session)
                                        .with(csrf())
                                        .with(ip(WRITER_IP))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "categoryCode",
                                                                "FREE",
                                                                "title",
                                                                marker + " " + title,
                                                                "content",
                                                                content,
                                                                "isAnonymous",
                                                                false))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String publicId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("publicId")
                        .asText();
        return postId(publicId);
    }

    void editPost(MockHttpSession session, long postId, String title, String content)
            throws Exception {
        mockMvc.perform(
                        put(POSTS + "/" + publicId(postId))
                                .session(session)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "categoryCode",
                                                        "FREE",
                                                        "title",
                                                        marker + " " + title,
                                                        "content",
                                                        content))))
                .andExpect(status().isOk());
    }

    /** 댓글을 쓰고 댓글 번호를 돌려준다. */
    long writeComment(MockHttpSession session, long postId, String content) throws Exception {
        mockMvc.perform(
                        post(POSTS + "/" + publicId(postId) + "/comments")
                                .session(session)
                                .with(csrf())
                                .with(ip(WRITER_IP))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of("content", content, "isAnonymous", false))))
                .andExpect(status().isCreated());
        return jdbc.queryForObject(
                "SELECT MAX(comment_id) FROM comment WHERE post_id = ?", Long.class, postId);
    }

    long postId(String publicId) {
        return jdbc.queryForObject(
                "SELECT post_id FROM post WHERE public_id = ?", Long.class, publicId);
    }

    String publicId(long postId) {
        return jdbc.queryForObject(
                "SELECT public_id FROM post WHERE post_id = ?", String.class, postId);
    }

    String postStatus(long postId) {
        return jdbc.queryForObject(
                "SELECT status FROM post WHERE post_id = ?", String.class, postId);
    }

    /** 대상의 가장 최근 판정 줄. */
    Map<String, Object> latest(String targetType, long targetId) {
        return jdbc.queryForMap(
                "SELECT * FROM content_moderation WHERE target_type = ? AND target_id = ?"
                        + " ORDER BY content_moderation_id DESC LIMIT 1",
                targetType,
                targetId);
    }

    int rowCount(String targetType, long targetId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM content_moderation WHERE target_type = ? AND target_id = ?",
                Integer.class,
                targetType,
                targetId);
    }

    static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
