package com.ikae.snowthing.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.aimember.AiMemberQueryRepository;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 1:1 문의가 작성자와 관리자 외에는 어떤 경로로도 보이지 않는지 확인한다. */
@SpringBootTest
@Transactional
class InquiryBoardTest {

    @Autowired private PostService postService;
    @Autowired private ReactionService reactionService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private SearchSourceRepository searchSourceRepository;
    @Autowired private AiMemberQueryRepository aiMemberQueryRepository;
    @Autowired private EntityManager entityManager;

    private CustomUserDetails writer;
    private CustomUserDetails other;
    private CustomUserDetails admin;
    private String inquiryId;

    @BeforeEach
    void setUp() {
        ensureCategory("FREE", "자유게시판");
        ensureCategory("INQUIRY", "1:1 문의");
        writer =
                new CustomUserDetails(
                        saveMember("inquiry-writer@example.com", "문의작성자", Role.ROLE_USER));
        other =
                new CustomUserDetails(
                        saveMember("inquiry-other@example.com", "다른회원", Role.ROLE_USER));
        admin =
                new CustomUserDetails(
                        saveMember("inquiry-admin@example.com", "운영자", Role.ROLE_ADMIN));
        inquiryId = postService.createPost(inquiry("결제 문의", false), writer, "127.0.0.1").publicId();
    }

    @Nested
    @DisplayName("작성")
    class Create {

        @Test
        @DisplayName("비로그인은 문의를 작성할 수 없다")
        void anonymousVisitorRejected() {
            assertThatThrownBy(
                            () -> postService.createPost(inquiry("문의", false), null, "127.0.0.1"))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_LOGIN_REQUIRED);
        }

        @Test
        @DisplayName("익명 문의는 거부한다")
        void anonymousInquiryRejected() {
            assertThatThrownBy(
                            () -> postService.createPost(inquiry("문의", true), writer, "127.0.0.1"))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("목록")
    class List_ {

        @Test
        @DisplayName("비로그인 문의 목록은 401이다")
        void anonymousVisitorRejected() {
            assertThatThrownBy(() -> postService.searchPostsByOffset(inquiryList(), null))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_LOGIN_REQUIRED);
            assertThatThrownBy(() -> postService.searchPostsByCursor(inquiryList(), null))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_LOGIN_REQUIRED);
        }

        @Test
        @DisplayName("회원은 본인 문의만, 관리자는 전체 문의를 본다")
        void writerSeesOwnAdminSeesAll() {
            String otherInquiry =
                    postService.createPost(inquiry("다른 문의", false), other, "127.0.0.1").publicId();

            assertThat(publicIds(postService.searchPostsByOffset(inquiryList(), writer).content()))
                    .containsExactly(inquiryId);
            assertThat(publicIds(postService.searchPostsByCursor(inquiryList(), other).content()))
                    .containsExactly(otherInquiry);
            assertThat(publicIds(postService.searchPostsByOffset(inquiryList(), admin).content()))
                    .containsExactlyInAnyOrder(inquiryId, otherInquiry);
        }

        @Test
        @DisplayName("전체 목록과 다른 게시판 목록에는 문의가 없다")
        void hiddenFromPublicLists() {
            String freePost =
                    postService
                            .createPost(
                                    new PostCreateRequest(
                                            "FREE", "자유글", "본문", false, null, List.of()),
                                    writer,
                                    "127.0.0.1")
                            .publicId();

            List<String> all =
                    publicIds(postService.searchPostsByOffset(request(null), admin).content());
            assertThat(all).contains(freePost).doesNotContain(inquiryId);
            assertThat(
                            publicIds(
                                    postService
                                            .searchPostsByCursor(request("FREE"), writer)
                                            .content()))
                    .doesNotContain(inquiryId);
            assertThat(publicIds(postService.getPostList(null, 0, 20).getContent()))
                    .doesNotContain(inquiryId);
        }

        @Test
        @DisplayName("관리자 댓글이 있으면 답변 완료, 지우면 답변 대기로 돌아간다")
        void answeredFollowsAdminComment() {
            commentService.createComment(inquiryId, comment("작성자 추가 질문"), writer, "127.0.0.1");
            assertThat(onlyItem(writer).answered()).isFalse();

            CommentResponse reply =
                    commentService.createComment(inquiryId, comment("운영자 답변"), admin, "127.0.0.1");
            assertThat(onlyItem(writer).answered()).isTrue();

            commentService.deleteComment(reply.commentId(), null, admin);
            assertThat(onlyItem(writer).answered()).isFalse();
        }

        @Test
        @DisplayName("문의가 아닌 목록은 답변 상태를 채우지 않는다")
        void answeredOnlyForInquiry() {
            postService.createPost(
                    new PostCreateRequest("FREE", "자유글", "본문", false, null, List.of()),
                    writer,
                    "127.0.0.1");

            assertThat(postService.searchPostsByOffset(request("FREE"), writer).content())
                    .allSatisfy(item -> assertThat(item.answered()).isNull());
        }
    }

    @Nested
    @DisplayName("상세와 수정")
    class Detail {

        @Test
        @DisplayName("작성자와 관리자만 상세를 보고, 나머지는 404다")
        void onlyWriterAndAdmin() {
            assertThat(postService.getPostDetail(inquiryId, writer, false).publicId())
                    .isEqualTo(inquiryId);
            assertThat(postService.getPostDetail(inquiryId, admin, false).publicId())
                    .isEqualTo(inquiryId);
            assertThatThrownBy(() -> postService.getPostDetail(inquiryId, other, false))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThatThrownBy(() -> postService.getPostDetail(inquiryId, null, false))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 회원의 삭제 시도도 404다")
        void otherMemberDeleteHidden() {
            assertThatThrownBy(() -> postService.deletePost(inquiryId, null, other))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("문의와 다른 게시판 사이의 카테고리 전환을 양방향으로 막는다")
        void categoryBoundary() {
            assertThatThrownBy(
                            () ->
                                    postService.updatePost(
                                            inquiryId,
                                            new PostUpdateRequest("FREE", "제목", "본문", null, null),
                                            writer))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_CATEGORY_CHANGE_NOT_ALLOWED);

            String freePost =
                    postService
                            .createPost(
                                    new PostCreateRequest(
                                            "FREE", "자유글", "본문", false, null, List.of()),
                                    writer,
                                    "127.0.0.1")
                            .publicId();
            assertThatThrownBy(
                            () ->
                                    postService.updatePost(
                                            freePost,
                                            new PostUpdateRequest(
                                                    "INQUIRY", "제목", "본문", null, null),
                                            writer))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_CATEGORY_CHANGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("댓글과 반응")
    class CommentAndReaction {

        @Test
        @DisplayName("다른 회원은 댓글을 읽거나 쓸 수 없다")
        void otherMemberCommentHidden() {
            CommentResponse root =
                    commentService.createComment(inquiryId, comment("운영자 답변"), admin, "127.0.0.1");

            assertThatThrownBy(() -> commentService.getCommentsByPost(inquiryId, null, 20, other))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThatThrownBy(
                            () ->
                                    commentService.getCommentReplies(
                                            root.commentId(), null, 20, other))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThatThrownBy(
                            () ->
                                    commentService.createComment(
                                            inquiryId, comment("끼어들기"), other, "127.0.0.1"))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(commentService.getCommentsByPost(inquiryId, null, 20, writer).comments())
                    .hasSize(1);
        }

        @Test
        @DisplayName("문의에는 익명 댓글을 달 수 없다")
        void anonymousCommentRejected() {
            assertThatThrownBy(
                            () ->
                                    commentService.createComment(
                                            inquiryId,
                                            new CommentCreateRequest(null, "익명 댓글", true, null),
                                            writer,
                                            "127.0.0.1"))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        @Test
        @DisplayName("문의에는 반응할 수 없고, 상세의 반응 상태는 비어 있다")
        void reactionHidden() {
            assertThatThrownBy(
                            () ->
                                    reactionService.apply(
                                            inquiryId,
                                            ReactionType.LIKE,
                                            writer,
                                            "127.0.0.1",
                                            null))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThat(reactionService.findActiveTypes(inquiryId, writer, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("근거 검색과 AI 멤버")
    class SearchAndAiMember {

        @Test
        @DisplayName("근거 검색 원문과 AI 멤버 최근 글에서 문의가 빠진다")
        void hiddenFromSearchAndAiMember() {
            entityManager.flush();

            assertThat(searchSourceRepository.findVisiblePostByPublicId(inquiryId)).isEmpty();
            assertThat(searchSourceRepository.findVisiblePosts())
                    .noneMatch(source -> inquiryId.equals(source.publicId()));
            assertThat(aiMemberQueryRepository.findRecentPosts(LocalDateTime.now().minusDays(1)))
                    .noneMatch(view -> inquiryId.equals(view.publicId()));
        }

        @Test
        @DisplayName("라이브톡 봇 위임으로는 작성자 본인의 문의도 읽지 못한다")
        void botDelegationCannotRead() {
            CustomUserDetails delegated = new BotDelegatedUserDetails(writer.getMember());

            assertThatThrownBy(() -> postService.searchPostsByOffset(inquiryList(), delegated))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INQUIRY_LOGIN_REQUIRED);
            assertThatThrownBy(() -> postService.getPostDetail(inquiryId, delegated, false))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
            assertThatThrownBy(
                            () -> commentService.getCommentsByPost(inquiryId, null, 20, delegated))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }
    }

    private PostListResponse onlyItem(CustomUserDetails viewer) {
        List<PostListResponse> content =
                postService.searchPostsByOffset(inquiryList(), viewer).content();
        assertThat(content).hasSize(1);
        return content.getFirst();
    }

    private static List<String> publicIds(List<PostListResponse> content) {
        return content.stream().map(PostListResponse::publicId).toList();
    }

    private static PostSearchRequest inquiryList() {
        return request("INQUIRY");
    }

    private static PostSearchRequest request(String categoryCode) {
        return new PostSearchRequest(categoryCode, null, null, null, null, 1, null, 20);
    }

    private static PostCreateRequest inquiry(String title, boolean anonymous) {
        return new PostCreateRequest("INQUIRY", title, "문의 본문", anonymous, null, List.of());
    }

    private static CommentCreateRequest comment(String content) {
        return new CommentCreateRequest(null, content, false, null);
    }

    private Member saveMember(String email, String nickname, Role role) {
        return memberRepository.save(
                Member.builder()
                        .email(email)
                        .password("encoded")
                        .nickname(nickname)
                        .role(role)
                        .build());
    }

    private void ensureCategory(String code, String name) {
        categoryRepository
                .findByCode(code)
                .orElseGet(
                        () ->
                                categoryRepository.save(
                                        PostCategory.builder().name(name).code(code).build()));
    }
}
