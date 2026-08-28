package com.ikae.snowthing.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.dto.PostCommentListResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@Transactional
class CommentServiceTest {

    @Autowired private CommentService commentService;

    @Autowired private PostService postService;

    @Autowired private MemberRepository memberRepository;

    @Autowired private PostCategoryRepository categoryRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private jakarta.persistence.EntityManager entityManager;

    private Member member1;
    private CustomUserDetails userDetails1;
    private PostResponse post;

    @BeforeEach
    void setUp() {
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));

        member1 =
                memberRepository.save(
                        Member.builder()
                                .email("commenter@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("댓글보더")
                                .role(Role.ROLE_USER)
                                .build());

        userDetails1 = new CustomUserDetails(member1);

        post =
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("댓글 테스트 게시글")
                                .content("게시글 본문")
                                .isAnonymous(false)
                                .build(),
                        userDetails1,
                        "127.0.0.1");
    }

    @Nested
    @DisplayName("댓글 작성 테스트")
    class CreateCommentTest {

        @Test
        @DisplayName("원댓글과 대댓글을 정상적으로 작성한다.")
        void createComment_success() {
            CommentResponse parent =
                    commentService.createComment(
                            post.publicId(),
                            CommentCreateRequest.builder()
                                    .content("원댓글입니다.")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            CommentResponse child =
                    commentService.createComment(
                            post.publicId(),
                            CommentCreateRequest.builder()
                                    .parentId(parent.commentId())
                                    .content("대댓글입니다.")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            assertThat(parent.commentId()).isNotNull();
            assertThat(child.parentId()).isEqualTo(parent.commentId());
        }

        @Test
        @DisplayName("존재하지 않는 부모 댓글 ID로 대댓글 작성 시 404 예외가 터진다.")
        void createComment_parentNotFound() {
            assertThatThrownBy(
                            () ->
                                    commentService.createComment(
                                            post.publicId(),
                                            CommentCreateRequest.builder()
                                                    .parentId(99999L)
                                                    .content("잘못된 부모 대댓글")
                                                    .isAnonymous(false)
                                                    .build(),
                                            userDetails1,
                                            "127.0.0.1"))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.PARENT_COMMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("댓글 트리 계층형 목록 조회 테스트")
    class GetCommentsTest {

        @Test
        @DisplayName("부모-자식 대댓글 트리 계층 구조가 정상 조립된다.")
        void getCommentsByPost_treeStructure() {
            CommentResponse parent1 =
                    commentService.createComment(
                            post.publicId(),
                            CommentCreateRequest.builder()
                                    .content("부모 댓글 1")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            commentService.createComment(
                    post.publicId(),
                    CommentCreateRequest.builder()
                            .parentId(parent1.commentId())
                            .content("자식 대댓글 1-1")
                            .isAnonymous(false)
                            .build(),
                    userDetails1,
                    "127.0.0.1");

            PostCommentListResponse response = commentService.getCommentsByPost(post.publicId());

            assertThat(response.totalCommentCount()).isEqualTo(2);
            assertThat(response.comments()).hasSize(1);
            assertThat(response.comments().get(0).children()).hasSize(1);
            assertThat(response.comments().get(0).children().get(0).content())
                    .isEqualTo("자식 대댓글 1-1");
        }

        @Test
        @DisplayName(
                "삭제된 부모 댓글은 Soft Delete 플래그(is_deleted=true, deleted_at NOT NULL)가 기록되고 본문이 '삭제된 댓글입니다.'로 표시된다.")
        void getCommentsByPost_deletedParentDisplay() {
            CommentResponse parent1 =
                    commentService.createComment(
                            post.publicId(),
                            CommentCreateRequest.builder()
                                    .content("지워질 부모 댓글")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            commentService.deleteComment(parent1.commentId(), null, userDetails1);

            // 1. 트리 목록 조회 시 본문 마스킹 검증
            PostCommentListResponse response = commentService.getCommentsByPost(post.publicId());
            assertThat(response.comments().get(0).isDeleted()).isTrue();
            assertThat(response.comments().get(0).content()).isEqualTo("삭제된 댓글입니다.");

            // 2. DB 물리 레코드 Soft Delete 상태 검증
            Object[] rawComment =
                    (Object[])
                            entityManager
                                    .createNativeQuery(
                                            "SELECT is_deleted, deleted_at FROM comment WHERE comment_id = :commentId")
                                    .setParameter("commentId", parent1.commentId())
                                    .getSingleResult();

            assertThat(rawComment[0]).isEqualTo(true);
            assertThat(rawComment[1]).isNotNull();
        }
    }
}
