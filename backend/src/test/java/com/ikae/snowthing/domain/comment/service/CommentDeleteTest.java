package com.ikae.snowthing.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
class CommentDeleteTest {

    @DynamicPropertySource
    static void useRealMySql(DynamicPropertyRegistry registry) {
        String testDbUrl = System.getenv("SNOWTHING_TEST_DB_URL");
        if (testDbUrl == null || testDbUrl.isBlank()) {
            return;
        }
        registry.add("spring.datasource.url", () -> testDbUrl);
        registry.add(
                "spring.datasource.username",
                () -> requiredEnvironmentVariable("SNOWTHING_TEST_DB_USERNAME"));
        registry.add(
                "spring.datasource.password",
                () -> requiredEnvironmentVariable("SNOWTHING_TEST_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }

    @Autowired private CommentService commentService;
    @Autowired private CommentRepository commentRepository;
    @Autowired private PostService postService;
    @Autowired private PostRepository postRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    private CustomUserDetails writerDetails;
    private CustomUserDetails otherDetails;
    private CustomUserDetails adminDetails;
    private PostResponse postResponse;

    @BeforeEach
    void setUp() {
        String fixtureId = UUID.randomUUID().toString().substring(0, 8);
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));

        Member writer =
                memberRepository.save(
                        new Member(
                                null,
                                "delete-writer-" + fixtureId + "@example.com",
                                passwordEncoder.encode("Password123!"),
                                "삭제작성자-" + fixtureId,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Role.ROLE_USER,
                                MemberStatus.ACTIVE));
        writerDetails = new CustomUserDetails(writer);

        Member other =
                memberRepository.save(
                        new Member(
                                null,
                                "delete-other-" + fixtureId + "@example.com",
                                passwordEncoder.encode("Password123!"),
                                "타인회원-" + fixtureId,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Role.ROLE_USER,
                                MemberStatus.ACTIVE));
        otherDetails = new CustomUserDetails(other);

        Member admin =
                memberRepository.save(
                        new Member(
                                null,
                                "delete-admin-" + fixtureId + "@example.com",
                                passwordEncoder.encode("Password123!"),
                                "최고관리자-" + fixtureId,
                                null,
                                null,
                                null,
                                null,
                                null,
                                Role.ROLE_ADMIN,
                                MemberStatus.ACTIVE));
        adminDetails = new CustomUserDetails(admin);

        postResponse =
                postService.createPost(
                        new PostCreateRequest(
                                "FREE", "삭제 테스트 게시글", "게시글 본문", false, null, List.of()),
                        writerDetails,
                        "127.0.0.1");
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("[성공 1] 일반 회원 본인 댓글 삭제 성공 (is_deleted = true, post.commentCount 1 차감 확인)")
        void deleteOwnCommentAsMember() {
            CommentResponse created = createMemberComment("본인 작성 댓글");

            commentService.deleteComment(created.commentId(), null, writerDetails);

            entityManager.flush();
            entityManager.clear();
            Comment deletedComment = commentRepository.findById(created.commentId()).orElseThrow();
            assertThat(deletedComment.isDeleted()).isTrue();
            assertThat(deletedComment.getDeletedAt()).isNotNull();

            Post post = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
            assertThat(post.getCommentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("[성공 2] 비회원 익명 댓글 올바른 비밀번호 입력 시 삭제 성공")
        void deleteAnonymousCommentWithCorrectPassword() {
            CommentResponse created = createGuestAnonymousComment("익명 작성 댓글", "anonPass1234");

            commentService.deleteComment(created.commentId(), "anonPass1234", null);

            entityManager.flush();
            entityManager.clear();
            Comment deletedComment = commentRepository.findById(created.commentId()).orElseThrow();
            assertThat(deletedComment.isDeleted()).isTrue();
            assertThat(deletedComment.getDeletedAt()).isNotNull();

            Post post = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
            assertThat(post.getCommentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("[성공 3] 최고 관리자(ROLE_ADMIN)가 타인/익명 댓글을 비밀번호 없이 강제 삭제 성공")
        void deleteCommentAsAdmin() {
            CommentResponse memberComment = createMemberComment("일반 회원 댓글");
            CommentResponse anonComment = createGuestAnonymousComment("비회원 익명 댓글", "anonPass1234");

            // 관리자는 회원 댓글을 비밀번호 없이 삭제 가능
            commentService.deleteComment(memberComment.commentId(), null, adminDetails);
            // 관리자는 익명 댓글도 비밀번호 없이 삭제 가능
            commentService.deleteComment(anonComment.commentId(), null, adminDetails);

            entityManager.flush();
            entityManager.clear();
            Comment deletedMemberComment =
                    commentRepository.findById(memberComment.commentId()).orElseThrow();
            Comment deletedAnonComment =
                    commentRepository.findById(anonComment.commentId()).orElseThrow();

            assertThat(deletedMemberComment.isDeleted()).isTrue();
            assertThat(deletedAnonComment.isDeleted()).isTrue();

            Post post = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
            assertThat(post.getCommentCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("[성공 4] 대댓글이 존재하는 부모 댓글 삭제 시 부모만 is_deleted = true 처리되고 하위 대댓글 정상 보존 확인")
        void deleteParentCommentPreservesReplies() {
            CommentResponse parent = createMemberComment("부모 댓글");
            CommentResponse reply1 = createReply(parent.commentId(), "대댓글 1");
            CommentResponse reply2 = createReply(parent.commentId(), "대댓글 2");

            Post postBeforeDelete =
                    postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
            assertThat(postBeforeDelete.getCommentCount()).isEqualTo(3);

            // 부모 댓글만 삭제
            commentService.deleteComment(parent.commentId(), null, writerDetails);

            entityManager.flush();
            entityManager.clear();
            Comment deletedParent = commentRepository.findById(parent.commentId()).orElseThrow();
            Comment activeReply1 = commentRepository.findById(reply1.commentId()).orElseThrow();
            Comment activeReply2 = commentRepository.findById(reply2.commentId()).orElseThrow();

            assertThat(deletedParent.isDeleted()).isTrue();
            assertThat(activeReply1.isDeleted()).isFalse();
            assertThat(activeReply2.isDeleted()).isFalse();

            Post postAfterDelete =
                    postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
            assertThat(postAfterDelete.getCommentCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("[실패 1] 로그인 회원이 타인의 댓글 삭제 시도 시 AUTH_002 (403 Forbidden) 검증")
        void rejectDeleteByOtherMember() {
            CommentResponse created = createMemberComment("타인이 삭제할 원본 댓글");

            assertThatThrownBy(
                            () ->
                                    commentService.deleteComment(
                                            created.commentId(), null, otherDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("[실패 2] 비회원 익명 댓글에 틀린 비밀번호 입력 시 POST_004 (403 Forbidden) 검증")
        void rejectDeleteWithWrongPassword() {
            CommentResponse created = createGuestAnonymousComment("익명 댓글", "correctPass1234");

            assertThatThrownBy(
                            () ->
                                    commentService.deleteComment(
                                            created.commentId(), "wrongPass9999", null))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ANON_PASSWORD);
        }

        @Test
        @DisplayName("[실패 3] 이미 Soft Delete된 댓글 재삭제 시도 시 COMMENT_001 (404 Not Found) 검증")
        void rejectDeleteOnAlreadyDeletedComment() {
            CommentResponse created = createMemberComment("이미 삭제될 댓글");
            commentService.deleteComment(created.commentId(), null, writerDetails);

            assertThatThrownBy(
                            () ->
                                    commentService.deleteComment(
                                            created.commentId(), null, writerDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("[실패 4] 존재하지 않는 댓글 ID 삭제 시도 시 COMMENT_001 (404 Not Found) 검증")
        void rejectDeleteOnNonExistentComment() {
            assertThatThrownBy(
                            () -> commentService.deleteComment(Long.MAX_VALUE, null, writerDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private CommentResponse createMemberComment(String content) {
        return commentService.createComment(
                postResponse.publicId(),
                new CommentCreateRequest(null, content, false, null),
                writerDetails,
                "127.0.0.1");
    }

    private CommentResponse createGuestAnonymousComment(String content, String password) {
        return commentService.createComment(
                postResponse.publicId(),
                new CommentCreateRequest(null, content, true, password),
                null,
                "127.0.0.1");
    }

    private CommentResponse createReply(Long parentId, String content) {
        return commentService.createComment(
                postResponse.publicId(),
                new CommentCreateRequest(parentId, content, false, null),
                writerDetails,
                "127.0.0.1");
    }
}
