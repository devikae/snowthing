package com.ikae.snowthing.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@Transactional
class CommentCreateTest {

    @Autowired private CommentService commentService;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    private CustomUserDetails userDetails;
    private PostResponse postResponse;

    @BeforeEach
    void setUp() {
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));

        Member member =
                memberRepository.save(
                        new Member(
                                null,
                                "comment-create@example.com",
                                passwordEncoder.encode("Password123!"),
                                "댓글작성자",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Role.ROLE_USER,
                                MemberStatus.ACTIVE));
        userDetails = new CustomUserDetails(member);

        postResponse =
                postService.createPost(
                        new PostCreateRequest(
                                "FREE", "댓글 생성 테스트", "게시글 본문", false, null, List.of()),
                        userDetails,
                        "127.0.0.1");
    }

    @Test
    @DisplayName("대댓글에 답글을 작성해도 최상위 루트 댓글 아래로 평탄화한다")
    void flattenReplyToRootComment() {
        CommentResponse root = createComment(null, "루트 댓글");
        CommentResponse reply = createComment(root.commentId(), "첫 번째 대댓글");

        CommentResponse nestedReply = createComment(reply.commentId(), "대댓글에 작성한 답글");

        entityManager.flush();
        entityManager.clear();
        Comment savedNestedReply =
                commentRepository.findById(nestedReply.commentId()).orElseThrow();
        assertThat(savedNestedReply.getParent().getId()).isEqualTo(root.commentId());
        assertThat(nestedReply.parentId()).isEqualTo(root.commentId());
    }

    @Test
    @DisplayName("루트 댓글의 활성 대댓글이 100개이면 COMMENT_004 예외를 발생시킨다")
    void rejectReplyWhenActiveReplyCountReachesLimit() {
        CommentResponse root = createComment(null, "루트 댓글");
        for (int index = 0; index < 100; index++) {
            createComment(root.commentId(), "대댓글 " + index);
        }

        assertThatThrownBy(() -> createComment(root.commentId(), "101번째 대댓글"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMENT_REPLY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("삭제된 대댓글은 활성 대댓글 100개 상한에서 제외한다")
    void excludeDeletedReplyFromActiveReplyLimit() {
        CommentResponse root = createComment(null, "루트 댓글");
        CommentResponse replyToDelete = null;
        for (int index = 0; index < 100; index++) {
            CommentResponse reply = createComment(root.commentId(), "대댓글 " + index);
            if (index == 0) {
                replyToDelete = reply;
            }
        }
        commentService.deleteComment(replyToDelete.commentId(), null, userDetails);

        CommentResponse replacement = createComment(root.commentId(), "삭제 후 새 대댓글");

        assertThat(replacement.parentId()).isEqualTo(root.commentId());
    }

    @Test
    @DisplayName("댓글 저장과 게시글 commentCount 증가는 같은 트랜잭션에서 동기화된다")
    void increasePostCommentCountWithCommentCreation() {
        createComment(null, "루트 댓글");
        createComment(null, "두 번째 루트 댓글");

        entityManager.flush();
        entityManager.clear();
        Post post = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
        assertThat(post.getCommentCount()).isEqualTo(2);
    }

    private CommentResponse createComment(Long parentId, String content) {
        return commentService.createComment(
                postResponse.publicId(),
                new CommentCreateRequest(parentId, content, false, null),
                userDetails,
                "127.0.0.1");
    }
}
