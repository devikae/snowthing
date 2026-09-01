package com.ikae.snowthing.domain.comment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
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
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@Transactional
class CommentCreateTest {

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

    private CustomUserDetails userDetails;
    private PostResponse postResponse;

    @BeforeEach
    void setUp() {
        String fixtureId = UUID.randomUUID().toString();
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));

        Member member =
                memberRepository.save(
                        new Member(
                                null,
                                "comment-create-" + fixtureId + "@example.com",
                                passwordEncoder.encode("Password123!"),
                                "댓글작성자-" + fixtureId,
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
    @DisplayName("로그인 회원이 루트 댓글을 생성하면 부모 없이 저장되고 댓글 수가 증가한다")
    void createRootCommentAsMember() {
        CommentResponse response = createComment(null, "회원 루트 댓글");

        entityManager.flush();
        entityManager.clear();
        Comment savedComment = commentRepository.findById(response.commentId()).orElseThrow();
        Post savedPost = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
        assertThat(savedComment.getParent()).isNull();
        assertThat(savedComment.getContent()).isEqualTo("회원 루트 댓글");
        assertThat(savedPost.getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인 회원은 작성자를 숨긴 익명 댓글을 생성할 수 있다")
    void createAnonymousCommentAsMember() {
        CommentResponse response =
                commentService.createComment(
                        postResponse.publicId(),
                        new CommentCreateRequest(null, "로그인 익명 댓글", true, null),
                        userDetails,
                        "127.0.0.1");

        Comment savedComment = commentRepository.findById(response.commentId()).orElseThrow();
        assertThat(savedComment.isAnonymous()).isTrue();
        assertThat(savedComment.getMember()).isNotNull();
        assertThat(savedComment.getAnonymousPassword()).isNull();
    }

    @Test
    @DisplayName("비로그인 사용자는 비밀번호를 제공하면 익명 댓글을 생성할 수 있다")
    void createAnonymousCommentAsGuest() {
        CommentResponse response =
                commentService.createComment(
                        postResponse.publicId(),
                        new CommentCreateRequest(null, "비로그인 익명 댓글", true, "1234"),
                        null,
                        "127.0.0.1");

        Comment savedComment = commentRepository.findById(response.commentId()).orElseThrow();
        assertThat(savedComment.getMember()).isNull();
        assertThat(passwordEncoder.matches("1234", savedComment.getAnonymousPassword())).isTrue();
    }

    @Test
    @DisplayName("삭제된 루트 댓글에도 새 대댓글을 생성할 수 있다")
    void createReplyUnderDeletedRootComment() {
        CommentResponse root = createComment(null, "삭제할 루트 댓글");
        commentService.deleteComment(root.commentId(), null, userDetails);

        CommentResponse reply = createComment(root.commentId(), "삭제된 루트의 새 대댓글");

        assertThat(reply.parentId()).isEqualTo(root.commentId());
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
        assertThat(commentRepository.countByParentIdAndIsDeletedFalse(root.commentId()))
                .isEqualTo(100);
        entityManager.clear();
        assertThat(
                        postRepository
                                .findByPublicId(postResponse.publicId())
                                .orElseThrow()
                                .getCommentCount())
                .isEqualTo(101);
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

    @Test
    @DisplayName("존재하지 않는 게시글에는 댓글을 생성할 수 없다")
    void rejectCommentForMissingPost() {
        assertThatThrownBy(
                        () ->
                                commentService.createComment(
                                        UUID.randomUUID().toString(),
                                        new CommentCreateRequest(null, "댓글", false, null),
                                        userDetails,
                                        "127.0.0.1"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("정상 상태가 아닌 게시글에는 댓글을 생성할 수 없다")
    void rejectCommentForBlockedPost() {
        Post post = postRepository.findByPublicId(postResponse.publicId()).orElseThrow();
        post.changeStatus(PostStatus.BLOCKED);
        entityManager.flush();

        assertThatThrownBy(() -> createComment(null, "차단 게시글 댓글"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 부모 댓글을 지정하면 댓글을 생성할 수 없다")
    void rejectMissingParentComment() {
        assertThatThrownBy(() -> createComment(Long.MAX_VALUE, "잘못된 부모"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARENT_COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 게시글의 댓글을 부모로 지정하면 댓글을 생성할 수 없다")
    void rejectParentCommentFromAnotherPost() {
        PostResponse anotherPost =
                postService.createPost(
                        new PostCreateRequest("FREE", "다른 게시글", "다른 본문", false, null, List.of()),
                        userDetails,
                        "127.0.0.1");
        CommentResponse anotherRoot =
                commentService.createComment(
                        anotherPost.publicId(),
                        new CommentCreateRequest(null, "다른 게시글 댓글", false, null),
                        userDetails,
                        "127.0.0.1");

        assertThatThrownBy(() -> createComment(anotherRoot.commentId(), "잘못 연결한 대댓글"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_COMMENT_PARENT);
    }

    @Test
    @DisplayName("비로그인 사용자는 일반 댓글을 생성할 수 없다")
    void rejectMemberCommentFromGuest() {
        assertThatThrownBy(
                        () ->
                                commentService.createComment(
                                        postResponse.publicId(),
                                        new CommentCreateRequest(null, "비로그인 일반 댓글", false, null),
                                        null,
                                        "127.0.0.1"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("DB에 존재하지 않는 회원 정보로는 댓글을 생성할 수 없다")
    void rejectCommentFromMissingMember() {
        Member missingMember =
                new Member(
                        UUID.randomUUID().toString(),
                        "missing@example.com",
                        "password",
                        "존재하지않는회원",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Role.ROLE_USER,
                        MemberStatus.ACTIVE);

        assertThatThrownBy(
                        () ->
                                commentService.createComment(
                                        postResponse.publicId(),
                                        new CommentCreateRequest(null, "댓글", false, null),
                                        new CustomUserDetails(missingMember),
                                        "127.0.0.1"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("비로그인 익명 사용자는 비밀번호 없이 댓글을 생성할 수 없다")
    void rejectAnonymousCommentWithoutPassword() {
        assertThatThrownBy(
                        () ->
                                commentService.createComment(
                                        postResponse.publicId(),
                                        new CommentCreateRequest(null, "익명 댓글", true, null),
                                        null,
                                        "127.0.0.1"))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("대댓글이 99개일 때 동시 요청 두 개 중 하나만 성공해 최종 100개를 유지한다")
    void allowOnlyOneConcurrentReplyAtLimitBoundary() throws Exception {
        CommentResponse root = createComment(null, "동시성 루트 댓글");
        for (int index = 0; index < 99; index++) {
            createComment(root.commentId(), "기존 대댓글 " + index);
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> results =
                    List.of(
                            executor.submit(
                                    () -> createConcurrentReply(root.commentId(), ready, start)),
                            executor.submit(
                                    () -> createConcurrentReply(root.commentId(), ready, start)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<ErrorCode> errorCodes =
                    Arrays.asList(
                            results.get(0).get(30, TimeUnit.SECONDS),
                            results.get(1).get(30, TimeUnit.SECONDS));
            assertThat(errorCodes)
                    .containsExactlyInAnyOrder(null, ErrorCode.COMMENT_REPLY_LIMIT_EXCEEDED);
            assertThat(commentRepository.countByParentIdAndIsDeletedFalse(root.commentId()))
                    .isEqualTo(100);
        } finally {
            executor.shutdownNow();
        }
    }

    private ErrorCode createConcurrentReply(
            Long rootCommentId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
        try {
            createComment(rootCommentId, "동시 대댓글");
            return null;
        } catch (CustomAuthException exception) {
            return exception.getErrorCode();
        }
    }

    private CommentResponse createComment(Long parentId, String content) {
        return commentService.createComment(
                postResponse.publicId(),
                new CommentCreateRequest(parentId, content, false, null),
                userDetails,
                "127.0.0.1");
    }
}
