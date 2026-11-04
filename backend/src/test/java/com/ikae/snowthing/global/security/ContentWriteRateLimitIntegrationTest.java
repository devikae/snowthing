package com.ikae.snowthing.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;

// 속성을 바꿔 컨텍스트를 새로 만들면 로컬 MySQL 연결 수가 넘치므로, 공유 컨텍스트의 빈에서 이 테스트 동안만 켠다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ContentWriteRateLimitIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository memberRepository;
    @Autowired PostCategoryRepository categoryRepository;
    @Autowired PostService postService;
    @Autowired ContentWriteRateLimiter contentWriteRateLimiter;

    private CustomUserDetails userDetails;
    private PostResponse post;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contentWriteRateLimiter, "enabled", true);
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS request_budget_window (budget_key VARCHAR(100) PRIMARY KEY,used_count INT NOT NULL,expires_at_millis BIGINT NOT NULL)");
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("write_limit_" + suffix + "@example.com")
                                .password("encoded")
                                .nickname("연타" + suffix)
                                .role(Role.ROLE_USER)
                                .build());
        userDetails = new CustomUserDetails(member);
        post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("연타 제한 테스트")
                                .content("내용")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");
    }

    @AfterEach
    void cleanup() {
        ReflectionTestUtils.setField(contentWriteRateLimiter, "enabled", false);
        jdbc.update(
                "DELETE FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:write:%'");
    }

    @Test
    void rapidRepeatedCommentIsRejectedAfterFirst() throws Exception {
        String body = "{\"content\":\"연타 댓글\",\"isAnonymous\":false}";
        var request =
                post("/api/v1/posts/" + post.publicId() + "/comments")
                        .with(csrf())
                        .with(user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body);

        mockMvc.perform(request).andExpect(status().isCreated());
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }
}
