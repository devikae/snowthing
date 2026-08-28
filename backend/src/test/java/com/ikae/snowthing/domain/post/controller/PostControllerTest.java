package com.ikae.snowthing.domain.post.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostDeleteRequest;
import com.ikae.snowthing.domain.post.dto.PostReactionRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.AnonymousVoterCookieManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private MemberRepository memberRepository;

    @Autowired private PostCategoryRepository categoryRepository;

    @Autowired private PostService postService;

    @Autowired private PasswordEncoder passwordEncoder;

    private Member member;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));

        member =
                memberRepository.save(
                        Member.builder()
                                .email("testuser@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("컨트롤러보더")
                                .role(Role.ROLE_USER)
                                .build());

        userDetails = new CustomUserDetails(member);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()));
    }

    @Test
    @DisplayName("POST /api/posts - 정상 게시글 작성 201 Created")
    void createPost_success() throws Exception {
        PostCreateRequest request =
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("컨트롤러 통합 테스트 제목")
                        .content("컨트롤러 통합 테스트 본문")
                        .isAnonymous(false)
                        .build();

        mockMvc.perform(
                        post("/api/v1/posts")
                                .with(csrf())
                                .with(user(userDetails))
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
        PostCreateRequest request =
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("")
                        .content("본문 내용")
                        .isAnonymous(false)
                        .build();

        mockMvc.perform(
                        post("/api/v1/posts")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/posts/{publicId} - 정상 상세 조회 200 OK")
    void getPostDetail_success() throws Exception {
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("상세조회 테스트")
                                .content("상세본문")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts/" + post.publicId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("상세조회 테스트"))
                .andExpect(jsonPath("$.viewCount").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/posts - 목록 페이징 조회 200 OK")
    void getPostList_success() throws Exception {
        postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("목록 테스트 1")
                        .content("본문 1")
                        .isAnonymous(false)
                        .build(),
                userDetails,
                "127.0.0.1");

        mockMvc.perform(
                        get("/api/v1/posts")
                                .param("categoryCode", "FREE")
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("목록 테스트 1"));
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{publicId} - 로그인 작성자 본인 정상 삭제 200 OK")
    void deletePost_writer_success() throws Exception {
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("작성자 삭제 테스트")
                                .content("본문 내용")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{publicId} - 관리자(ROLE_ADMIN)는 타인의 일반글도 삭제 가능 200 OK")
    void deletePost_admin_success() throws Exception {
        Member adminMember =
                memberRepository.save(
                        Member.builder()
                                .email("admin@snowthing.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("최고관리자")
                                .role(Role.ROLE_ADMIN)
                                .build());
        CustomUserDetails adminUserDetails = new CustomUserDetails(adminMember);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                adminUserDetails, null, adminUserDetails.getAuthorities()));

        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("타 회원 글")
                                .content("본문 내용")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .with(user(adminUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{publicId} - 타 회원이 일반 회원글 삭제 시도 시 403 Forbidden")
    void deletePost_otherMember_forbidden() throws Exception {
        Member otherMember =
                memberRepository.save(
                        Member.builder()
                                .email("other@snowthing.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("남의글탐내는자")
                                .role(Role.ROLE_USER)
                                .build());
        CustomUserDetails otherUserDetails = new CustomUserDetails(otherMember);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                otherUserDetails, null, otherUserDetails.getAuthorities()));

        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("원작성자 글")
                                .content("본문 내용")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .with(user(otherUserDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/posts/{publicId} - 비회원 익명글에 올바른 비밀번호를 Request Body로 전송 시 삭제 200 OK")
    void deletePost_anonymous_success_withRequestBody() throws Exception {
        SecurityContextHolder.clearContext();

        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("익명 글")
                                .content("본문 내용")
                                .isAnonymous(true)
                                .anonymousPassword("SecretPass123!")
                                .build(),
                        null,
                        "127.0.0.1");

        PostDeleteRequest deleteRequest = new PostDeleteRequest("SecretPass123!");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/posts/{publicId} - 비회원 익명글에 잘못된 비밀번호를 Request Body로 전송 시 403 Forbidden")
    void deletePost_anonymous_forbidden_withWrongPassword() throws Exception {
        SecurityContextHolder.clearContext();

        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("익명 글")
                                .content("본문 내용")
                                .isAnonymous(true)
                                .anonymousPassword("SecretPass123!")
                                .build(),
                        null,
                        "127.0.0.1");

        PostDeleteRequest deleteRequest = new PostDeleteRequest("WrongPass123!");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_004"));
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/posts/{publicId} - 관계없는 제3자 로그인 사용자가 익명글의 비밀번호를 알고 Request Body로 입력 시 삭제 200 OK")
    void deletePost_anonymous_thirdPartyMemberWithPassword_success() throws Exception {
        // 1. 비회원이 익명글 작성
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("익명글")
                                .content("비회원이 쓴 글")
                                .isAnonymous(true)
                                .anonymousPassword("SharedPassword123!")
                                .build(),
                        null,
                        "127.0.0.1");

        // 2. 관계없는 제3의 로그인 사용자가 로그인한 상태
        Member thirdParty =
                memberRepository.save(
                        Member.builder()
                                .email("thirdparty@snowthing.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("제3자회원")
                                .role(Role.ROLE_USER)
                                .build());
        CustomUserDetails thirdUserDetails = new CustomUserDetails(thirdParty);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                thirdUserDetails, null, thirdUserDetails.getAuthorities()));

        // 3. 올바른 비밀번호를 Request Body에 담아 삭제 요청 -> 정책상 삭제 성공
        PostDeleteRequest deleteRequest = new PostDeleteRequest("SharedPassword123!");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName(
            "DELETE /api/v1/posts/{publicId} - 관계없는 제3자 로그인 사용자가 익명글에 틀린 비밀번호를 입력 시 403 Forbidden")
    void deletePost_anonymous_thirdPartyMemberWithWrongPassword_forbidden() throws Exception {
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("익명글")
                                .content("비회원이 쓴 글")
                                .isAnonymous(true)
                                .anonymousPassword("SharedPassword123!")
                                .build(),
                        null,
                        "127.0.0.1");

        Member thirdParty =
                memberRepository.save(
                        Member.builder()
                                .email("thirdparty2@snowthing.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("제3자회원2")
                                .role(Role.ROLE_USER)
                                .build());
        CustomUserDetails thirdUserDetails = new CustomUserDetails(thirdParty);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                thirdUserDetails, null, thirdUserDetails.getAuthorities()));

        PostDeleteRequest deleteRequest = new PostDeleteRequest("WrongPassword!");

        mockMvc.perform(
                        delete("/api/v1/posts/" + post.publicId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_004"));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{publicId} - viewed_posts 쿠키가 있으면 조회수를 중복 증가시키지 않는다")
    void getPostDetail_withViewedCookie_doesNotIncreaseViewCountAgain() throws Exception {
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("조회수 쿠키 테스트")
                                .content("본문")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");

        Cookie viewedCookie = new Cookie("viewed_posts", "[" + post.publicId() + "]");

        mockMvc.perform(get("/api/v1/posts/" + post.publicId()).cookie(viewedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(0));
    }

    @Test
    @DisplayName(
            "POST /api/v1/posts/{publicId}/reactions - 비로그인 익명 사용자는 anonymous_voter_id 쿠키로 추천을 토글한다")
    void reactToPost_anonymousUser_usesAnonymousVoterCookie() throws Exception {
        PostResponse post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("익명 추천 컨트롤러 테스트")
                                .content("본문")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");
        PostReactionRequest request = new PostReactionRequest(ReactionType.LIKE);

        SecurityContextHolder.clearContext();

        var firstResult =
                mockMvc.perform(
                                post("/api/v1/posts/" + post.publicId() + "/reactions")
                                        .with(csrf())
                                        .with(anonymous())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(
                                cookie().exists(
                                                AnonymousVoterCookieManager
                                                        .ANONYMOUS_VOTER_COOKIE_NAME))
                        .andExpect(jsonPath("$.isToggledOn").value(true))
                        .andExpect(jsonPath("$.likeCount").value(1))
                        .andReturn();

        Cookie anonymousVoterCookie =
                firstResult
                        .getResponse()
                        .getCookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME);

        mockMvc.perform(
                        post("/api/v1/posts/" + post.publicId() + "/reactions")
                                .with(csrf())
                                .with(anonymous())
                                .cookie(anonymousVoterCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isToggledOn").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/posts - 웹 Offset 페이징 조회 성공")
    void searchPostsByOffset_success() throws Exception {
        postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("웹 페이징 글 1")
                        .content("본문")
                        .isAnonymous(false)
                        .build(),
                userDetails,
                "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("웹 페이징 글 1"));
    }

    @Test
    @DisplayName("GET /api/v1/posts/scroll - 모바일 Cursor 페이징 조회 성공")
    void searchPostsByCursor_success() throws Exception {
        postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title("커서 페이징 글 1")
                        .content("본문")
                        .isAnonymous(false)
                        .build(),
                userDetails,
                "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts/scroll").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("커서 페이징 글 1"));
    }
}
