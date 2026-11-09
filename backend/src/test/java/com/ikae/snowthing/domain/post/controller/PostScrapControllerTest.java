package com.ikae.snowthing.domain.post.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 스크랩 API의 인증, CSRF, 응답 모양과 글 상세의 scrapped 값을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostScrapControllerTest {

    private static final String SCRAP_URL = "/api/v1/posts/{publicId}/scrap";
    private static final String MY_SCRAPS_URL = "/api/v1/members/me/scraps";

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;

    private CustomUserDetails member;
    private Post post;

    @BeforeEach
    void setUp() {
        PostCategory free =
                categoryRepository
                        .findByCode("FREE")
                        .orElseGet(() -> categoryRepository.save(new PostCategory("FREE", "FREE")));
        String unique = "scrap-api-" + UUID.randomUUID().toString().substring(0, 8);
        member =
                new CustomUserDetails(
                        memberRepository.save(
                                Member.builder()
                                        .email(unique + "@example.com")
                                        .nickname(unique)
                                        .role(Role.ROLE_USER)
                                        .build()));
        post =
                postRepository.saveAndFlush(
                        Post.builder()
                                .member(member.getMember())
                                .category(free)
                                .title("스크랩할 글")
                                .content("본문")
                                .writerIp("127.0.0.1")
                                .build());
    }

    @Test
    @DisplayName("비로그인은 스크랩 추가, 취소, 목록 모두 401이다")
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(put(SCRAP_URL, post.getPublicId()).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SCRAP_001"));
        mockMvc.perform(delete(SCRAP_URL, post.getPublicId()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(MY_SCRAPS_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰 없는 스크랩 요청은 거부한다")
    void csrfRequired() throws Exception {
        mockMvc.perform(put(SCRAP_URL, post.getPublicId()).with(user(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("스크랩하면 상세에 scrapped=true, 목록에 글이 나오고 취소하면 사라진다")
    void scrapFlow() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{publicId}", post.getPublicId()).with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scrapped").value(false));

        mockMvc.perform(put(SCRAP_URL, post.getPublicId()).with(user(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scrapped").value(true))
                .andExpect(jsonPath("$.changed").value(true));
        mockMvc.perform(put(SCRAP_URL, post.getPublicId()).with(user(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        mockMvc.perform(get("/api/v1/posts/{publicId}", post.getPublicId()).with(user(member)))
                .andExpect(jsonPath("$.scrapped").value(true));
        mockMvc.perform(get("/api/v1/posts/{publicId}", post.getPublicId()))
                .andExpect(jsonPath("$.scrapped").value(false));
        mockMvc.perform(get(MY_SCRAPS_URL).with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].postPublicId").value(post.getPublicId()))
                .andExpect(jsonPath("$.content[0].title").value("스크랩할 글"))
                .andExpect(jsonPath("$.pageInfo.hasNext").value(false));

        mockMvc.perform(delete(SCRAP_URL, post.getPublicId()).with(user(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scrapped").value(false))
                .andExpect(jsonPath("$.changed").value(true));
        mockMvc.perform(get(MY_SCRAPS_URL).with(user(member)))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("없는 글 스크랩은 404다")
    void missingPost() throws Exception {
        mockMvc.perform(
                        put(SCRAP_URL, UUID.randomUUID().toString())
                                .with(user(member))
                                .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_001"));
    }
}
