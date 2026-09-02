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
import com.ikae.snowthing.domain.comment.dto.CommentUpdateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentUpdateResponse;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
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
class CommentUpdateTest {

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
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    private CustomUserDetails writerDetails;
    private CustomUserDetails otherDetails;
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
                                "update-writer-" + fixtureId + "@example.com",
                                passwordEncoder.encode("Password123!"),
                                "수정작성자-" + fixtureId,
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
                                "update-other-" + fixtureId + "@example.com",
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

        postResponse =
                postService.createPost(
                        new PostCreateRequest(
                                "FREE", "수정 테스트 게시글", "게시글 본문", false, null, List.of()),
                        writerDetails,
                        "127.0.0.1");
    }

    @Nested
    @DisplayName("성공 케이스")
    class SuccessCase {

        @Test
        @DisplayName("[성공 1] 일반 회원 본인 댓글 수정 성공")
        void updateOwnCommentAsMember() {
            CommentResponse created = createMemberComment("수정 전 내용");

            CommentUpdateResponse updated =
                    commentService.updateComment(
                            created.commentId(),
                            new CommentUpdateRequest("수정 후 내용", null),
                            writerDetails);

            assertThat(updated.commentId()).isEqualTo(created.commentId());
            assertThat(updated.content()).isEqualTo("수정 후 내용");
            assertThat(updated.updatedAt()).isNotNull();

            entityManager.flush();
            entityManager.clear();
            Comment savedComment = commentRepository.findById(created.commentId()).orElseThrow();
            assertThat(savedComment.getContent()).isEqualTo("수정 후 내용");
        }

        @Test
        @DisplayName("[성공 2] 비회원 익명 댓글 올바른 비밀번호 입력 시 수정 성공")
        void updateAnonymousCommentWithCorrectPassword() {
            CommentResponse created = createGuestAnonymousComment("익명 수정 전", "mypass1234");

            CommentUpdateResponse updated =
                    commentService.updateComment(
                            created.commentId(),
                            new CommentUpdateRequest("익명 수정 후", "mypass1234"),
                            null);

            assertThat(updated.content()).isEqualTo("익명 수정 후");

            entityManager.flush();
            entityManager.clear();
            Comment savedComment = commentRepository.findById(created.commentId()).orElseThrow();
            assertThat(savedComment.getContent()).isEqualTo("익명 수정 후");
        }
    }

    @Nested
    @DisplayName("실패 케이스")
    class FailureCase {

        @Test
        @DisplayName("[실패 1] 로그인 회원이 타인의 댓글 수정 시도 시 ACCESS_DENIED (403)")
        void rejectUpdateByOtherMember() {
            CommentResponse created = createMemberComment("원본 댓글");

            assertThatThrownBy(
                            () ->
                                    commentService.updateComment(
                                            created.commentId(),
                                            new CommentUpdateRequest("타인이 수정", null),
                                            otherDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("[실패 2] 비회원 익명 댓글에 잘못된 비밀번호 입력 시 INVALID_ANON_PASSWORD (403)")
        void rejectUpdateWithWrongPassword() {
            CommentResponse created = createGuestAnonymousComment("익명 원본", "correct1234");

            assertThatThrownBy(
                            () ->
                                    commentService.updateComment(
                                            created.commentId(),
                                            new CommentUpdateRequest("수정 시도", "wrong9999"),
                                            null))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ANON_PASSWORD);
        }

        @Test
        @DisplayName("[실패 3] 이미 Soft Delete된 댓글 수정 시도 시 COMMENT_NOT_FOUND (404)")
        void rejectUpdateOnDeletedComment() {
            CommentResponse created = createMemberComment("삭제할 댓글");
            commentService.deleteComment(created.commentId(), null, writerDetails);

            assertThatThrownBy(
                            () ->
                                    commentService.updateComment(
                                            created.commentId(),
                                            new CommentUpdateRequest("삭제 후 수정", null),
                                            writerDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("[실패 4] 존재하지 않는 댓글 ID로 수정 시도 시 COMMENT_NOT_FOUND (404)")
        void rejectUpdateOnNonExistentComment() {
            assertThatThrownBy(
                            () ->
                                    commentService.updateComment(
                                            Long.MAX_VALUE,
                                            new CommentUpdateRequest("수정 시도", null),
                                            writerDetails))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("[실패 5] 비회원 익명 댓글에 비밀번호 누락 시 INVALID_ANON_PASSWORD (403)")
        void rejectUpdateWithNullPassword() {
            CommentResponse created = createGuestAnonymousComment("익명 원본", "pass1234");

            assertThatThrownBy(
                            () ->
                                    commentService.updateComment(
                                            created.commentId(),
                                            new CommentUpdateRequest("비밀번호 없이 수정", null),
                                            null))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_ANON_PASSWORD);
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
}
