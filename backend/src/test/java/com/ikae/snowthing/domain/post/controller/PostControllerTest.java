package com.ikae.snowthing.domain.post.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostReactionRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.AnonymousVoterCookieManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostCategoryRepository categoryRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member member;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        categoryRepository.findByCode("FREE")
            .orElseGet(() -> categoryRepository.save(PostCategory.builder().name("자유게시판").code("FREE").build()));

        member = memberRepository.save(Member.builder()
            .email("testuser@example.com")
            .password(passwordEncoder.encode("Password123!"))
            .nickname("컨트롤러보더")
            .role(Role.ROLE_USER)
            .build());

        userDetails = new CustomUserDetails(member);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @Test
    @DisplayName("POST /api/posts - 정상 게시글 작성 201 Created")
    void createPost_success() throws Exception {
        PostCreateRequest request = PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("컨트롤러 통합 테스트 제목")
            .content("컨트롤러 통합 테스트 본문")
            .isAnonymous(false)
            .build();

        mockMvc.perform(post("/api/v1/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.publicId").exists())
            .andExpect(jsonPath("$.title").value("컨트롤러 통합 테스트 제목"))
            .andExpect(jsonPath("$.writerName").value("컨트롤러보더"));
    }

    @Test
    @DisplayName("POST /api/v1/posts - 빈 제목 입력 시 400 Bad Request")
    void createPost_emptyTitle_badRequest() throws Exception {
        PostCreateRequest request = PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("")
            .content("본문 내용")
            .isAnonymous(false)
            .build();

        mockMvc.perform(post("/api/v1/posts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/posts/{publicId} - 정상 상세 조회 200 OK")
    void getPostDetail_success() throws Exception {
        PostResponse post = postService.createPost(PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("상세조회 테스트")
            .content("상세본문")
            .isAnonymous(false)
            .build(), userDetails, "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts/" + post.publicId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("상세조회 테스트"))
            .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/posts - 목록 페이징 조회 200 OK")
    void getPostList_success() throws Exception {
        postService.createPost(PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("목록 테스트 1")
            .content("본문 1")
            .isAnonymous(false)
            .build(), userDetails, "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts")
                .param("categoryCode", "FREE")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].title").value("목록 테스트 1"));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{publicId} - 정상 삭제 200 OK")
    void deletePost_success() throws Exception {
        PostResponse post = postService.createPost(PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("삭제 테스트")
            .content("본문 내용")
            .isAnonymous(false)
            .build(), userDetails, "127.0.0.1");

        mockMvc.perform(delete("/api/v1/posts/" + post.publicId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/posts/{publicId} - viewed_posts 쿠키가 있으면 조회수를 중복 증가시키지 않는다")
    void getPostDetail_withViewedCookie_doesNotIncreaseViewCountAgain() throws Exception {
        PostResponse post = postService.createPost(PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("조회수 쿠키 테스트")
            .content("본문")
            .isAnonymous(false)
            .build(), userDetails, "127.0.0.1");

        Cookie viewedCookie = new Cookie("viewed_posts", "[" + post.publicId() + "]");

        mockMvc.perform(get("/api/v1/posts/" + post.publicId()).cookie(viewedCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.viewCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/posts/{publicId}/reactions - 비로그인 익명 사용자는 anonymous_voter_id 쿠키로 추천을 토글한다")
    void reactToPost_anonymousUser_usesAnonymousVoterCookie() throws Exception {
        PostResponse post = postService.createPost(PostCreateRequest.builder()
            .categoryCode("FREE")
            .title("익명 추천 컨트롤러 테스트")
            .content("본문")
            .isAnonymous(false)
            .build(), userDetails, "127.0.0.1");
        PostReactionRequest request = new PostReactionRequest(ReactionType.LIKE);

        SecurityContextHolder.clearContext();

        var firstResult = mockMvc.perform(post("/api/v1/posts/" + post.publicId() + "/reactions").with(csrf())
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME))
            .andExpect(jsonPath("$.isToggledOn").value(true))
            .andExpect(jsonPath("$.likeCount").value(1))
            .andReturn();

        Cookie anonymousVoterCookie = firstResult.getResponse()
            .getCookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME);

        mockMvc.perform(post("/api/v1/posts/" + post.publicId() + "/reactions").with(csrf())
                .with(anonymous())
                .cookie(anonymousVoterCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isToggledOn").value(false))
            .andExpect(jsonPath("$.likeCount").value(0));
    }
}
