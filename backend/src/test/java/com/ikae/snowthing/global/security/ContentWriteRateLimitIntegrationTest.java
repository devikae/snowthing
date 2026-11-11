package com.ikae.snowthing.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void guestPasswordAttemptsAreLimitedPerIp() throws Exception {
        var request =
                delete("/api/v1/posts/" + post.publicId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousPassword\":\"0000\"}");

        // 회원 글이라 비밀번호가 맞을 수 없다. 앞의 세 번은 권한 오류, 네 번째는 제한에 걸린다.
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(request)
                    .andExpect(
                            result ->
                                    org.assertj.core.api.Assertions.assertThat(
                                                    result.getResponse().getStatus())
                                            .isBetween(400, 428));
        }
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void loggedInMemberGuessingAnotherPostPasswordIsAlsoLimited() throws Exception {
        // 로그인 회원이 남의 글에 비밀번호를 실어 보내도 비회원과 같은 횟수 제한을 받는다.
        // 예전에는 로그인하면 제한을 건너뛰어 0000~9999를 끝없이 대입할 수 있었다.
        // 다른 테스트, 이전 실행과 IP 버킷이 겹치지 않게 임의의 주소 하나를 정해 모든 시도에 쓴다(제한 횟수는 DB에 남는다).
        String clientIp =
                "203.0.113." + (1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(254));
        var request =
                delete("/api/v1/posts/" + post.publicId())
                        .with(csrf())
                        .with(user(otherMember()))
                        .with(
                                mock -> {
                                    mock.setRemoteAddr(clientIp);
                                    return mock;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"anonymousPassword\":\"0000\"}");

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(request)
                    .andExpect(
                            result ->
                                    org.assertj.core.api.Assertions.assertThat(
                                                    result.getResponse().getStatus())
                                            .isBetween(400, 428));
        }
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void writerEditingOwnPostWithoutPasswordIsNotCountedAsGuessing() throws Exception {
        // 본인 글은 비밀번호 없이 고치므로 비밀번호 제한을 쓰지 않는다. 연달아 고쳐도 429가 나지 않는다.
        String body = "{\"title\":\"고친 제목\",\"content\":\"고친 내용\",\"categoryCode\":\"FREE\"}";
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(
                            put("/api/v1/posts/" + post.publicId())
                                    .with(csrf())
                                    .with(user(userDetails))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isOk());
        }
    }

    private CustomUserDetails otherMember() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return new CustomUserDetails(
                memberRepository.save(
                        Member.builder()
                                .email("guesser_" + suffix + "@example.com")
                                .password("encoded")
                                .nickname("대입" + suffix)
                                .role(Role.ROLE_USER)
                                .build()));
    }

    @Test
    void guestCannotPumpReactionsOnOnePostByDroppingCookies() throws Exception {
        // 비회원 추천은 익명게시판 글에서만 받는다(IKA-82).
        categoryRepository
                .findByCode("ANONYMOUS")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder()
                                                .name("익명게시판")
                                                .code("ANONYMOUS")
                                                .build()));
        PostResponse anonymousPost =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("ANONYMOUS")
                                .title("비회원 연타 제한 테스트")
                                .content("내용")
                                .isAnonymous(true)
                                .anonymousPassword("Anon1234!")
                                .build(),
                        null,
                        "127.0.0.1");
        var request = put("/api/v1/posts/" + anonymousPost.publicId() + "/reaction").with(csrf());

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(request).andExpect(status().isOk());
        }
        mockMvc.perform(request)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void memberReactionIsNotLimitedByGuestReactionBudget() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(
                            put("/api/v1/posts/" + post.publicId() + "/reaction")
                                    .with(csrf())
                                    .with(user(userDetails)))
                    .andExpect(status().isOk());
        }
    }
}
