package com.ikae.snowthing.domain.comment.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private MemberRepository memberRepository;

    @Autowired private PostCategoryRepository categoryRepository;

    @Autowired private PostService postService;

    @Autowired private CommentService commentService;

    @Autowired private PasswordEncoder passwordEncoder;

    private Member member;
    private CustomUserDetails userDetails;
    private PostResponse post;

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
                                .email("comment_test@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("댓글컨트롤러보더")
                                .role(Role.ROLE_USER)
                                .build());

        userDetails = new CustomUserDetails(member);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()));

        post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("댓글 테스트용 게시글")
                                .content("내용")
                                .isAnonymous(false)
                                .build(),
                        userDetails,
                        "127.0.0.1");
    }

    @Test
    @DisplayName("POST /api/v1/posts/{publicId}/comments - 댓글 작성 201 Created")
    void createComment_success() throws Exception {
        CommentCreateRequest request =
                CommentCreateRequest.builder().content("통합 테스트 댓글 내용").isAnonymous(false).build();

        mockMvc.perform(
                        post("/api/v1/posts/" + post.publicId() + "/comments")
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").exists())
                .andExpect(jsonPath("$.content").value("통합 테스트 댓글 내용"));
    }

    @ParameterizedTest(name = "익명 비밀번호 {0}자는 허용된다")
    @ValueSource(ints = {4, 20})
    @DisplayName("POST /api/v1/posts/{publicId}/comments - 익명 비밀번호 허용 경계값")
    void createAnonymousComment_acceptsValidPasswordBoundary(int passwordLength) throws Exception {
        CommentCreateRequest request =
                CommentCreateRequest.builder()
                        .content("비로그인 익명 댓글")
                        .isAnonymous(true)
                        .anonymousPassword("1".repeat(passwordLength))
                        .build();

        mockMvc.perform(
                        post("/api/v1/posts/" + post.publicId() + "/comments")
                                .with(csrf())
                                .with(anonymous())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").exists());
    }

    @ParameterizedTest(name = "익명 비밀번호 {0}자는 거부된다")
    @ValueSource(ints = {3, 21})
    @DisplayName("POST /api/v1/posts/{publicId}/comments - 익명 비밀번호 거부 경계값")
    void createAnonymousComment_rejectsInvalidPasswordBoundary(int passwordLength)
            throws Exception {
        CommentCreateRequest request =
                CommentCreateRequest.builder()
                        .content("비로그인 익명 댓글")
                        .isAnonymous(true)
                        .anonymousPassword("1".repeat(passwordLength))
                        .build();

        mockMvc.perform(
                        post("/api/v1/posts/" + post.publicId() + "/comments")
                                .with(csrf())
                                .with(anonymous())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("GET /api/v1/posts/{publicId}/comments - 댓글 목록 조회 200 OK")
    void getComments_success() throws Exception {
        commentService.createComment(
                post.publicId(),
                CommentCreateRequest.builder().content("댓글 1").isAnonymous(false).build(),
                userDetails,
                "127.0.0.1");

        mockMvc.perform(get("/api/v1/posts/" + post.publicId() + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments").isArray())
                .andExpect(jsonPath("$.totalCommentCount").value(1));
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{commentId} - 댓글 Soft Delete 삭제 200 OK")
    void deleteComment_success() throws Exception {
        CommentResponse comment =
                commentService.createComment(
                        post.publicId(),
                        CommentCreateRequest.builder().content("삭제될 댓글").isAnonymous(false).build(),
                        userDetails,
                        "127.0.0.1");

        mockMvc.perform(
                        delete("/api/v1/comments/" + comment.commentId())
                                .with(csrf())
                                .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }
}
