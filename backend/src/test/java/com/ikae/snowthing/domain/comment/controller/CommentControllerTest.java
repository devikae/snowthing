package com.ikae.snowthing.domain.comment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.dto.CommentUpdateRequest;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
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

    @Autowired private CommentRepository commentRepository;

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

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - 작성자 인증과 CSRF 토큰으로 수정하면 200 OK")
    void updateComment_success() throws Exception {
        CommentResponse comment = createMemberComment("수정 전 댓글");
        CommentUpdateRequest request = new CommentUpdateRequest("수정 후 댓글", null);

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(comment.commentId()))
                .andExpect(jsonPath("$.content").value("수정 후 댓글"))
                .andExpect(jsonPath("$.updatedAt").exists());

        Comment updated = commentRepository.findById(comment.commentId()).orElseThrow();
        assertThat(updated.getContent()).isEqualTo("수정 후 댓글");
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - 다른 회원이면 AUTH_002와 403을 반환한다")
    void updateComment_forbiddenForOtherMember() throws Exception {
        CommentResponse comment = createMemberComment("작성자 댓글");
        Member otherMember =
                memberRepository.save(
                        Member.builder()
                                .email("comment-update-other@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("댓글수정타인")
                                .role(Role.ROLE_USER)
                                .build());
        CustomUserDetails otherUserDetails = new CustomUserDetails(otherMember);

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(csrf())
                                .with(user(otherUserDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommentUpdateRequest("타인의 수정", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - 공백 본문은 COMMON_001과 400을 반환한다")
    void updateComment_rejectsInvalidRequestBody() throws Exception {
        CommentResponse comment = createMemberComment("수정 전 댓글");

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":\"   \",\"anonymousPassword\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - JSON 역직렬화 실패 시 400을 반환한다")
    void updateComment_rejectsMalformedJson() throws Exception {
        CommentResponse comment = createMemberComment("수정 전 댓글");

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"content\":{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - CSRF 토큰이 없으면 403을 반환한다")
    void updateComment_rejectsRequestWithoutCsrfToken() throws Exception {
        CommentResponse comment = createMemberComment("수정 전 댓글");

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommentUpdateRequest("수정 시도", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{commentId} - 비회원 익명 댓글은 비밀번호로 수정하면 200 OK")
    void updateGuestAnonymousComment_success() throws Exception {
        CommentResponse comment =
                commentService.createComment(
                        post.publicId(),
                        new CommentCreateRequest(null, "비회원 익명 댓글", true, "password1234"),
                        null,
                        "127.0.0.1");

        mockMvc.perform(
                        put("/api/v1/comments/{commentId}", comment.commentId())
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CommentUpdateRequest(
                                                        "비회원 수정 댓글", "password1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("비회원 수정 댓글"));
    }

    private CommentResponse createMemberComment(String content) {
        return commentService.createComment(
                post.publicId(),
                new CommentCreateRequest(null, content, false, null),
                userDetails,
                "127.0.0.1");
    }
}
