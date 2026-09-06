package com.ikae.snowthing.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentReplyListResponse;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.dto.PostCommentListResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
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
@AutoConfigureMockMvc
@Transactional
class CommentReadTest {

    @Autowired private CommentService commentService;
    @Autowired private PostService postService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    private CustomUserDetails userDetails;
    private CustomUserDetails otherUserDetails;
    private PostResponse post;

    @BeforeEach
    void setUp() {
        categoryRepository
                .findByCode("FREE")
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name("자유게시판").code("FREE").build()));
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("comment-read@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("댓글조회보더")
                                .profileImageUrl("https://example.com/profile.jpg")
                                .role(Role.ROLE_USER)
                                .build());
        userDetails = new CustomUserDetails(member);
        Member otherMember =
                memberRepository.save(
                        Member.builder()
                                .email("comment-read-other@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("댓글조회다른사용자")
                                .role(Role.ROLE_USER)
                                .build());
        otherUserDetails = new CustomUserDetails(otherMember);
        post = createPost("댓글 조회 게시글");
    }

    @Nested
    @DisplayName("성공 시나리오")
    class SuccessCases {

        @Test
        @DisplayName("루트 댓글을 중복 없이 커서 페이징하고 마지막 페이지를 판별한다")
        void rootCursorPaging() {
            CommentResponse first = createRoot("루트 1");
            CommentResponse second = createRoot("루트 2");
            CommentResponse third = createRoot("루트 3");

            PostCommentListResponse firstPage =
                    commentService.getCommentsByPost(post.publicId(), null, 2);
            PostCommentListResponse secondPage =
                    commentService.getCommentsByPost(post.publicId(), firstPage.nextCursor(), 2);

            assertThat(firstPage.comments())
                    .extracting(CommentResponse::commentId)
                    .containsExactly(first.commentId(), second.commentId());
            assertThat(firstPage.hasNext()).isTrue();
            assertThat(firstPage.nextCursor()).isEqualTo(second.commentId());
            assertThat(secondPage.comments())
                    .extracting(CommentResponse::commentId)
                    .containsExactly(third.commentId());
            assertThat(secondPage.hasNext()).isFalse();
            assertThat(secondPage.nextCursor()).isNull();
        }

        @Test
        @DisplayName("동일 생성 시각에는 commentId 오름차순으로 결정론적 정렬한다")
        void sameCreatedAtUsesIdTieBreaker() {
            CommentResponse first = createRoot("동시각 1");
            CommentResponse second = createRoot("동시각 2");
            LocalDateTime sameTime = LocalDateTime.of(2026, 9, 1, 12, 0);
            jdbcTemplate.update(
                    "UPDATE comment SET created_at = :createdAt WHERE comment_id IN (:ids)",
                    new MapSqlParameterSource("createdAt", sameTime)
                            .addValue(
                                    "ids",
                                    java.util.List.of(first.commentId(), second.commentId())));

            PostCommentListResponse response =
                    commentService.getCommentsByPost(post.publicId(), null, 20);

            assertThat(response.comments())
                    .extracting(CommentResponse::commentId)
                    .containsExactly(first.commentId(), second.commentId());
        }

        @Test
        @DisplayName("루트별 대댓글은 5개만 프리뷰하고 이후 항목을 분리 API로 조회한다")
        void topFivePreviewAndSeparatedReplies() throws Exception {
            CommentResponse root = createRoot("프리뷰 루트");
            for (int i = 1; i <= 7; i++) {
                createReply(root.commentId(), "대댓글 " + i);
            }

            PostCommentListResponse comments =
                    commentService.getCommentsByPost(post.publicId(), null, 20);
            CommentResponse rootResponse = comments.comments().getFirst();
            Long fifthReplyId = rootResponse.previewReplies().getLast().commentId();
            CommentReplyListResponse remainder =
                    commentService.getCommentReplies(root.commentId(), fifthReplyId, 20);

            assertThat(rootResponse.replyCount()).isEqualTo(7);
            assertThat(rootResponse.previewReplies()).hasSize(5);
            assertThat(rootResponse.hasMoreReplies()).isTrue();
            assertThat(remainder.replies())
                    .extracting(CommentResponse::content)
                    .containsExactly("대댓글 6", "대댓글 7");
            assertThat(remainder.totalReplyCount()).isEqualTo(7);
            assertThat(remainder.hasNext()).isFalse();

            mockMvc.perform(
                            get("/api/v1/comments/{commentId}/replies", root.commentId())
                                    .param("cursor", fifthReplyId.toString())
                                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rootCommentId").value(root.commentId()))
                    .andExpect(jsonPath("$.replies.length()").value(2))
                    .andExpect(jsonPath("$.replies[0].canEdit").value(false))
                    .andExpect(jsonPath("$.replies[0].requiresPassword").value(false))
                    .andExpect(jsonPath("$.replies[0].ownerPublicId").doesNotExist());
        }

        @Test
        @DisplayName("삭제된 대댓글도 placeholder로 노출하며 대댓글 수와 더보기 기준을 일치시킨다")
        void deletedRepliesKeepResponseCountsConsistent() {
            CommentResponse root = createRoot("삭제 대댓글 집계 루트");
            List<CommentResponse> replies =
                    java.util.stream.IntStream.rangeClosed(1, 7)
                            .mapToObj(index -> createReply(root.commentId(), "대댓글 " + index))
                            .toList();
            commentService.deleteComment(replies.get(0).commentId(), null, userDetails);
            commentService.deleteComment(replies.get(1).commentId(), null, userDetails);

            PostCommentListResponse response =
                    commentService.getCommentsByPost(post.publicId(), null, 20);
            CommentResponse rootResponse = response.comments().getFirst();

            assertThat(rootResponse.replyCount()).isEqualTo(7);
            assertThat(rootResponse.previewReplies()).hasSize(5);
            assertThat(rootResponse.previewReplies().getFirst().isDeleted()).isTrue();
            assertThat(rootResponse.hasMoreReplies()).isTrue();

            CommentReplyListResponse repliesResponse =
                    commentService.getCommentReplies(root.commentId(), null, 20);
            assertThat(repliesResponse.totalReplyCount()).isEqualTo(7);
            assertThat(repliesResponse.replies()).hasSize(7);
        }

        @Test
        @DisplayName("삭제 루트는 활성 대댓글이 있으면 placeholder로 남고 모두 삭제되면 은닉한다")
        void deletionVisibilityPolicy() {
            CommentResponse root = createRoot("삭제될 루트");
            CommentResponse reply = createReply(root.commentId(), "남아 있는 대댓글");
            commentService.deleteComment(root.commentId(), null, userDetails);

            PostCommentListResponse withActiveReply =
                    commentService.getCommentsByPost(post.publicId(), null, 20);
            assertThat(withActiveReply.comments()).hasSize(1);
            assertThat(withActiveReply.comments().getFirst().content()).isEqualTo("삭제된 댓글입니다.");
            assertThat(withActiveReply.comments().getFirst().replyCount()).isEqualTo(1);

            commentService.deleteComment(reply.commentId(), null, userDetails);
            PostCommentListResponse allDeleted =
                    commentService.getCommentsByPost(post.publicId(), null, 20);
            assertThat(allDeleted.comments()).isEmpty();
        }

        @Test
        @DisplayName("조회 응답의 루트 및 프리뷰 컬렉션은 변경할 수 없다")
        void responseCollectionsAreImmutable() {
            CommentResponse root = createRoot("불변 루트");
            createReply(root.commentId(), "불변 대댓글");
            PostCommentListResponse response =
                    commentService.getCommentsByPost(post.publicId(), null, 20);

            assertThatThrownBy(() -> response.comments().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> response.comments().getFirst().previewReplies().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("댓글 수정 UI 권한은 서버의 작성 주체별 권한 정책과 일치한다")
        void editPermissionMetadataMatchesOwnershipPolicy() {
            CommentResponse memberComment = createRoot("회원 댓글");
            CommentResponse memberAnonymousComment =
                    commentService.createComment(
                            post.publicId(),
                            new CommentCreateRequest(null, "로그인 익명 댓글", true, null),
                            userDetails,
                            "127.0.0.1");
            CommentResponse guestAnonymousComment =
                    commentService.createComment(
                            post.publicId(),
                            new CommentCreateRequest(null, "비회원 익명 댓글", true, "password1234"),
                            null,
                            "127.0.0.1");

            PostCommentListResponse ownerView =
                    commentService.getCommentsByPost(post.publicId(), null, 20, userDetails);
            assertThat(findComment(ownerView, memberComment.commentId()).canEdit()).isTrue();
            assertThat(findComment(ownerView, memberComment.commentId()).requiresPassword())
                    .isFalse();
            assertThat(findComment(ownerView, memberAnonymousComment.commentId()).canEdit())
                    .isTrue();
            assertThat(
                            findComment(ownerView, memberAnonymousComment.commentId())
                                    .requiresPassword())
                    .isFalse();
            assertThat(findComment(ownerView, guestAnonymousComment.commentId()).canEdit())
                    .isTrue();
            assertThat(findComment(ownerView, guestAnonymousComment.commentId()).requiresPassword())
                    .isTrue();

            PostCommentListResponse otherView =
                    commentService.getCommentsByPost(post.publicId(), null, 20, otherUserDetails);
            assertThat(findComment(otherView, memberComment.commentId()).canEdit()).isFalse();
            assertThat(findComment(otherView, memberAnonymousComment.commentId()).canEdit())
                    .isFalse();
            assertThat(findComment(otherView, guestAnonymousComment.commentId()).canEdit())
                    .isTrue();
        }

        @Test
        @DisplayName("대댓글 분리 조회에도 동일한 수정 UI 권한을 적용한다")
        void separatedReplyPermissionMetadataMatchesOwnershipPolicy() {
            CommentResponse root = createRoot("권한 확인 루트");
            CommentResponse memberAnonymousReply =
                    commentService.createComment(
                            post.publicId(),
                            new CommentCreateRequest(root.commentId(), "로그인 익명 대댓글", true, null),
                            userDetails,
                            "127.0.0.1");

            CommentReplyListResponse ownerView =
                    commentService.getCommentReplies(root.commentId(), null, 20, userDetails);
            CommentReplyListResponse otherView =
                    commentService.getCommentReplies(root.commentId(), null, 20, otherUserDetails);

            assertThat(findReply(ownerView, memberAnonymousReply.commentId()).canEdit()).isTrue();
            assertThat(findReply(otherView, memberAnonymousReply.commentId()).canEdit()).isFalse();
        }
    }

    @Nested
    @DisplayName("실패 시나리오")
    class FailureCases {

        @Test
        @DisplayName("존재하지 않는 게시글 댓글 조회는 POST_NOT_FOUND를 반환한다")
        void postNotFound() {
            assertErrorCode(
                    () -> commentService.getCommentsByPost("missing-public-id", null, 20),
                    ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("댓글 페이지 크기가 허용 범위를 벗어나면 COMMENT_INVALID_PAGE_SIZE를 반환한다")
        void invalidPageSize() throws Exception {
            assertErrorCode(
                    () -> commentService.getCommentsByPost(post.publicId(), null, 0),
                    ErrorCode.COMMENT_INVALID_PAGE_SIZE);
            assertErrorCode(
                    () -> commentService.getCommentsByPost(post.publicId(), null, 51),
                    ErrorCode.COMMENT_INVALID_PAGE_SIZE);

            mockMvc.perform(
                            get("/api/v1/posts/{publicId}/comments", post.publicId())
                                    .param("size", "51"))
                    .andExpect(status().isBadRequest())
                    .andExpect(
                            jsonPath("$.code")
                                    .value(ErrorCode.COMMENT_INVALID_PAGE_SIZE.getCode()));
        }

        @Test
        @DisplayName("다른 게시글의 루트 커서를 사용하면 COMMENT_NOT_FOUND를 반환한다")
        void cursorFromDifferentPost() {
            PostResponse anotherPost = createPost("다른 게시글");
            CommentResponse foreignCursor =
                    commentService.createComment(
                            anotherPost.publicId(),
                            request("다른 루트", null),
                            userDetails,
                            "127.0.0.1");

            assertErrorCode(
                    () ->
                            commentService.getCommentsByPost(
                                    post.publicId(), foreignCursor.commentId(), 20),
                    ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("대댓글 ID를 루트 분리 조회 대상으로 사용하면 COMMENT_NOT_FOUND를 반환한다")
        void replyCannotBeUsedAsRoot() {
            CommentResponse root = createRoot("루트");
            CommentResponse child = createReply(root.commentId(), "대댓글");

            assertErrorCode(
                    () -> commentService.getCommentReplies(child.commentId(), null, 20),
                    ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 루트의 대댓글 커서를 사용하면 COMMENT_NOT_FOUND를 반환한다")
        void cursorFromDifferentRoot() {
            CommentResponse firstRoot = createRoot("첫 루트");
            CommentResponse secondRoot = createRoot("둘째 루트");
            CommentResponse foreignReply = createReply(secondRoot.commentId(), "다른 루트 대댓글");

            assertErrorCode(
                    () ->
                            commentService.getCommentReplies(
                                    firstRoot.commentId(), foreignReply.commentId(), 20),
                    ErrorCode.COMMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("일반 회원 댓글은 writerIp가 null이고, 익명 댓글은 마스킹된 IP를 반환한다")
        void getComments_masksWriterIpOnlyForAnonymous() {
            CommentResponse memberComment = createRoot("회원 댓글");
            commentService.createComment(
                    post.publicId(),
                    CommentCreateRequest.builder()
                            .content("익명 댓글")
                            .isAnonymous(true)
                            .anonymousPassword("1234")
                            .build(),
                    null,
                    "211.234.10.20");

            PostCommentListResponse response =
                    commentService.getCommentsByPost(post.publicId(), null, 20, null);

            CommentResponse foundMember = findComment(response, memberComment.commentId());
            assertThat(foundMember.isAnonymous()).isFalse();
            assertThat(foundMember.writerIp()).isNull();

            CommentResponse foundAnon =
                    response.comments().stream()
                            .filter(CommentResponse::isAnonymous)
                            .findFirst()
                            .orElseThrow();
            assertThat(foundAnon.isAnonymous()).isTrue();
            assertThat(foundAnon.writerIp()).isEqualTo("211.234.***.***");
        }
    }

    private PostResponse createPost(String title) {
        return postService.createPost(
                PostCreateRequest.builder()
                        .categoryCode("FREE")
                        .title(title)
                        .content("본문")
                        .isAnonymous(false)
                        .build(),
                userDetails,
                "127.0.0.1");
    }

    private CommentResponse createRoot(String content) {
        return commentService.createComment(
                post.publicId(), request(content, null), userDetails, "211.234.10.20");
    }

    private CommentResponse createReply(Long rootId, String content) {
        return commentService.createComment(
                post.publicId(), request(content, rootId), userDetails, "175.120.10.20");
    }

    private CommentCreateRequest request(String content, Long parentId) {
        return CommentCreateRequest.builder()
                .parentId(parentId)
                .content(content)
                .isAnonymous(false)
                .build();
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    private CommentResponse findComment(PostCommentListResponse response, Long commentId) {
        return response.comments().stream()
                .filter(comment -> comment.commentId().equals(commentId))
                .findFirst()
                .orElseThrow();
    }

    private CommentResponse findReply(CommentReplyListResponse response, Long commentId) {
        return response.replies().stream()
                .filter(comment -> comment.commentId().equals(commentId))
                .findFirst()
                .orElseThrow();
    }
}
