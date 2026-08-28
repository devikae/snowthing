package com.ikae.snowthing.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostImageRepository;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@Transactional
class PostServiceTest {

    @Autowired private PostService postService;

    @Autowired private PostRepository postRepository;

    @Autowired private PostCategoryRepository categoryRepository;

    @Autowired private MemberRepository memberRepository;

    @Autowired private PostReactionRepository reactionRepository;

    @Autowired private PostImageRepository imageRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private jakarta.persistence.EntityManager entityManager;

    private Member member1;
    private Member member2;
    private CustomUserDetails userDetails1;
    private CustomUserDetails userDetails2;
    private PostCategory freeCategory;

    @BeforeEach
    void setUp() {
        postRepository.deleteAll();

        member1 =
                memberRepository.save(
                        Member.builder()
                                .email("user1@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("보더1호")
                                .role(Role.ROLE_USER)
                                .build());

        member2 =
                memberRepository.save(
                        Member.builder()
                                .email("user2@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname("보더2호")
                                .role(Role.ROLE_USER)
                                .build());

        userDetails1 = new CustomUserDetails(member1);
        userDetails2 = new CustomUserDetails(member2);

        freeCategory =
                categoryRepository
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        categoryRepository.save(
                                                PostCategory.builder()
                                                        .name("자유게시판")
                                                        .code("FREE")
                                                        .build()));
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @DisplayName("로그인 회원이 정상적으로 게시글을 작성한다.")
        void createPost_success_member() {
            PostCreateRequest request =
                    PostCreateRequest.builder()
                            .categoryCode("FREE")
                            .title("오늘 설질 어떤가요?")
                            .content("휘팍 설질 최고입니다.")
                            .isAnonymous(false)
                            .imageUrls(List.of("https://cdn.example.com/1.jpg"))
                            .build();

            PostResponse response = postService.createPost(request, userDetails1, "127.0.0.1");

            assertThat(response.publicId()).isNotNull();
            assertThat(response.title()).isEqualTo("오늘 설질 어떤가요?");
            assertThat(response.writerName()).isEqualTo("보더1호");
            assertThat(response.status()).isEqualTo(PostStatus.NORMAL);

            Post savedPost = postRepository.findByPublicId(response.publicId()).orElseThrow();
            assertThat(savedPost.isHasImage()).isTrue();
            assertThat(savedPost.getImages()).hasSize(1);
        }

        @Test
        @DisplayName("비회원이 익명 게시글을 정상적으로 작성한다.")
        void createPost_success_anonymous() {
            PostCreateRequest request =
                    PostCreateRequest.builder()
                            .categoryCode("FREE")
                            .title("익명 질문입니다.")
                            .content("입문용 데크 추천해 주세요.")
                            .isAnonymous(true)
                            .anonymousPassword("Anon1234!")
                            .build();

            PostResponse response = postService.createPost(request, null, "192.168.1.100");

            assertThat(response.publicId()).isNotNull();
            assertThat(response.writerName()).contains("익명");
        }

        @Test
        @DisplayName("비인증 유저가 회원 게시글(isAnonymous=false) 작성을 시도하면 예외가 터진다.")
        void createPost_unauthorized() {
            PostCreateRequest request =
                    PostCreateRequest.builder()
                            .categoryCode("FREE")
                            .title("비인증 게시글")
                            .content("본문 내용")
                            .isAnonymous(false)
                            .build();

            assertThatThrownBy(() -> postService.createPost(request, null, "127.0.0.1"))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("게시글 상세 및 목록 조회 테스트")
    class GetPostTest {

        @Test
        @DisplayName("정상 게시글 상세 조회 시 조회수가 1 증가한다.")
        void getPostDetail_success() {
            PostCreateRequest request =
                    PostCreateRequest.builder()
                            .categoryCode("FREE")
                            .title("조회수 테스트")
                            .content("상세 내용")
                            .isAnonymous(false)
                            .build();

            PostResponse created = postService.createPost(request, userDetails1, "127.0.0.1");

            PostDetailResponse detail =
                    postService.getPostDetail(created.publicId(), userDetails1, true);

            assertThat(detail.title()).isEqualTo("조회수 테스트");
            assertThat(detail.viewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 publicId 조회 시 404 예외가 터진다.")
        void getPostDetail_notFound() {
            assertThatThrownBy(
                            () ->
                                    postService.getPostDetail(
                                            "non-existent-uuid", userDetails1, true))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("목록 조회 시 본문(content)이 제외되고 최신순/id역순으로 페이징 조회된다.")
        void getPostList_success() {
            for (int i = 1; i <= 5; i++) {
                postService.createPost(
                        PostCreateRequest.builder()
                                .categoryCode("FREE")
                                .title("테스트 글 " + i)
                                .content("본문 내용 " + i)
                                .isAnonymous(false)
                                .build(),
                        userDetails1,
                        "127.0.0.1");
            }

            Page<PostListResponse> page = postService.getPostList("FREE", 0, 10);

            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getContent().get(0).title()).isEqualTo("테스트 글 5");
        }

        @Test
        @DisplayName("잘못된 페이지 크기(size=0 이하) 입력 시 예외가 터진다.")
        void getPostList_invalidPageSize() {
            assertThatThrownBy(() -> postService.getPostList("FREE", 0, 0))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("게시글 수정 및 삭제 권한 테스트")
    class UpdateAndDeletePostTest {

        private PostResponse createdPost;

        @BeforeEach
        void setUpPost() {
            createdPost =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("수정전 제목")
                                    .content("수정전 본문")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");
        }

        @Test
        @DisplayName("작성자 본인은 게시글을 정상 수정한다.")
        void updatePost_success_writer() {
            PostUpdateRequest updateReq =
                    PostUpdateRequest.builder()
                            .categoryCode("FREE")
                            .title("수정후 제목")
                            .content("수정후 본문")
                            .build();

            PostResponse updated =
                    postService.updatePost(createdPost.publicId(), updateReq, userDetails1);

            assertThat(updated.title()).isEqualTo("수정후 제목");
        }

        @Test
        @DisplayName("작성자 본인이 게시글 수정 시 이미지 목록을 최종 상태로 교체하고 hasImage를 동기화한다.")
        void updatePost_replaceImages_success() {
            PostUpdateRequest updateReq =
                    PostUpdateRequest.builder()
                            .categoryCode("FREE")
                            .title("이미지 수정 제목")
                            .content("이미지 수정 본문")
                            .imageUrls(
                                    List.of(
                                            "https://cdn.example.com/updated-1.jpg",
                                            "https://cdn.example.com/updated-2.jpg"))
                            .build();

            postService.updatePost(createdPost.publicId(), updateReq, userDetails1);
            postRepository.flush();

            Post savedPost = postRepository.findByPublicId(createdPost.publicId()).orElseThrow();
            List<PostImage> images =
                    imageRepository.findByPostIdOrderBySortOrderAsc(savedPost.getId());

            assertThat(savedPost.isHasImage()).isTrue();
            assertThat(images)
                    .extracting(PostImage::getImageUrl)
                    .containsExactly(
                            "https://cdn.example.com/updated-1.jpg",
                            "https://cdn.example.com/updated-2.jpg");
        }

        @Test
        @DisplayName("작성자 본인이 게시글 수정 시 이미지 목록을 비우면 첨부 이미지를 제거하고 hasImage를 false로 동기화한다.")
        void updatePost_removeImages_success() {
            PostResponse postWithImage =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("이미지 있는 글")
                                    .content("본문")
                                    .isAnonymous(false)
                                    .imageUrls(List.of("https://cdn.example.com/original.jpg"))
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            PostUpdateRequest updateReq =
                    PostUpdateRequest.builder()
                            .categoryCode("FREE")
                            .title("이미지 제거 제목")
                            .content("이미지 제거 본문")
                            .imageUrls(List.of())
                            .build();

            postService.updatePost(postWithImage.publicId(), updateReq, userDetails1);
            postRepository.flush();

            Post savedPost = postRepository.findByPublicId(postWithImage.publicId()).orElseThrow();
            List<PostImage> images =
                    imageRepository.findByPostIdOrderBySortOrderAsc(savedPost.getId());

            assertThat(savedPost.isHasImage()).isFalse();
            assertThat(images).isEmpty();
        }

        @Test
        @DisplayName("타 회원이 수정 시도 시 403 Forbidden 예외가 터진다.")
        void updatePost_forbidden_otherUser() {
            PostUpdateRequest updateReq =
                    PostUpdateRequest.builder()
                            .categoryCode("FREE")
                            .title("타인 해킹 시도")
                            .content("해킹 본문")
                            .build();

            assertThatThrownBy(
                            () ->
                                    postService.updatePost(
                                            createdPost.publicId(), updateReq, userDetails2))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName(
                "작성자 본인은 Soft Delete로 게시글을 정상 삭제하며 DB에 is_deleted=true, status=DELETED, deleted_at이 기록된다.")
        void deletePost_success_softDelete() {
            postService.deletePost(createdPost.publicId(), null, userDetails1);

            // 1. 서비스 레벨 조회 시 404 예외 검증
            assertThatThrownBy(
                            () -> postService.getPostDetail(createdPost.publicId(), userDetails1))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);

            // 2. DB 물리 레코드 Soft Delete 상태 검증 (SQLRestriction 우회 Native Query)
            Object[] rawPost =
                    (Object[])
                            entityManager
                                    .createNativeQuery(
                                            "SELECT is_deleted, status, deleted_at FROM post WHERE public_id = :publicId")
                                    .setParameter("publicId", createdPost.publicId())
                                    .getSingleResult();

            assertThat(rawPost[0]).isEqualTo(true);
            assertThat(rawPost[1]).isEqualTo("DELETED");
            assertThat(rawPost[2]).isNotNull();
        }

        @Test
        @DisplayName("이미 삭제된 게시글에 대해 삭제를 재시도하면 404 예외가 터진다.")
        void deletePost_alreadyDeleted() {
            postService.deletePost(createdPost.publicId(), null, userDetails1);

            assertThatThrownBy(
                            () ->
                                    postService.deletePost(
                                            createdPost.publicId(), null, userDetails1))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("추천/비추천 중복 제약 조건 테스트")
    class ReactionTest {

        @Test
        @DisplayName("추천/비추천 클릭 시 토글(ON/OFF) 동작하며 카운트가 +1, -1로 정상 갱신된다.")
        void reactToPost_toggle_success() {
            PostResponse post =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("추천 테스트 글")
                                    .content("내용")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            // 1회 클릭: Toggle ON (+1)
            ReactionToggleResponse res1 =
                    postService.reactToPost(
                            post.publicId(), ReactionType.LIKE, userDetails1, "127.0.0.1", null);
            assertThat(res1.isToggledOn()).isTrue();
            assertThat(res1.likeCount()).isEqualTo(1);

            // 2회 클릭: Toggle OFF (-1)
            ReactionToggleResponse res2 =
                    postService.reactToPost(
                            post.publicId(), ReactionType.LIKE, userDetails1, "127.0.0.1", null);
            assertThat(res2.isToggledOn()).isFalse();
            assertThat(res2.likeCount()).isEqualTo(0);

            // 추천과 비추천은 독립 투표 가능 (비추천 1회 클릭 ON)
            ReactionToggleResponse res3 =
                    postService.reactToPost(
                            post.publicId(), ReactionType.DISLIKE, userDetails1, "127.0.0.1", null);
            assertThat(res3.isToggledOn()).isTrue();
            assertThat(res3.dislikeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("비로그인 익명 사용자도 anonymousVoterId 기반으로 추천/비추천 토글을 수행하며 DB에 정상 저장/삭제된다.")
        void reactToPost_anonymousVoter_success() {
            PostResponse post =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("익명 추천 테스트 글")
                                    .content("내용")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            // 1. 비로그인 익명 사용자 1회 추천 (Toggle ON)
            ReactionToggleResponse res1 =
                    postService.reactToPost(
                            post.publicId(), ReactionType.LIKE, null, "10.0.0.1", "anon-voter-1");
            assertThat(res1.isToggledOn()).isTrue();
            assertThat(res1.likeCount()).isEqualTo(1);

            // DB 물리 저장 검증 (member_id IS NULL, anonymous_voter_id = 'anon-voter-1', writer_ip =
            // '10.0.0.1')
            Object[] rawReaction =
                    (Object[])
                            entityManager
                                    .createNativeQuery(
                                            "SELECT member_id, anonymous_voter_id, writer_ip, type FROM post_reaction WHERE anonymous_voter_id = :voterId")
                                    .setParameter("voterId", "anon-voter-1")
                                    .getSingleResult();

            assertThat(rawReaction[0]).isNull();
            assertThat(rawReaction[1]).isEqualTo("anon-voter-1");
            assertThat(rawReaction[2]).isEqualTo("10.0.0.1");
            assertThat(rawReaction[3]).isEqualTo("LIKE");

            // 2. 비로그인 익명 사용자 재클릭 (Toggle OFF)
            ReactionToggleResponse res2 =
                    postService.reactToPost(
                            post.publicId(), ReactionType.LIKE, null, "10.0.0.1", "anon-voter-1");
            assertThat(res2.isToggledOn()).isFalse();
            assertThat(res2.likeCount()).isEqualTo(0);

            // DB 물리 삭제 검증
            Number count =
                    (Number)
                            entityManager
                                    .createNativeQuery(
                                            "SELECT count(*) FROM post_reaction WHERE anonymous_voter_id = :voterId")
                                    .setParameter("voterId", "anon-voter-1")
                                    .getSingleResult();
            assertThat(count.intValue()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("게시글 상태(NORMAL, HIDDEN, BLOCKED, DRAFT, DELETED)별 접근 정책 테스트")
    class PostStatusPolicyTest {

        @Autowired private com.ikae.snowthing.domain.comment.service.CommentService commentService;

        @Test
        @DisplayName(
                "목록 조회(Offset, Cursor) 시 NORMAL 상태가 아닌 HIDDEN, BLOCKED, DRAFT, DELETED 글은 일반 목록에서 제외된다.")
        void searchPosts_excludesNonNormalPosts() {
            // 1. NORMAL 글 생성
            Post normalPost =
                    postRepository.save(
                            Post.builder()
                                    .member(member1)
                                    .category(freeCategory)
                                    .title("정상 공개 게시글")
                                    .content("정상 내용")
                                    .writerIp("127.0.0.1")
                                    .status(PostStatus.NORMAL)
                                    .build());

            // 2. HIDDEN, BLOCKED, DRAFT 글 생성 (isDeleted=false)
            Post hiddenPost =
                    postRepository.save(
                            Post.builder()
                                    .member(member1)
                                    .category(freeCategory)
                                    .title("숨김 게시글")
                                    .content("숨김 내용")
                                    .writerIp("127.0.0.1")
                                    .status(PostStatus.HIDDEN)
                                    .build());

            Post blockedPost =
                    postRepository.save(
                            Post.builder()
                                    .member(member1)
                                    .category(freeCategory)
                                    .title("차단 게시글")
                                    .content("차단 내용")
                                    .writerIp("127.0.0.1")
                                    .status(PostStatus.BLOCKED)
                                    .build());

            Post draftPost =
                    postRepository.save(
                            Post.builder()
                                    .member(member1)
                                    .category(freeCategory)
                                    .title("임시저장 게시글")
                                    .content("임시저장 내용")
                                    .writerIp("127.0.0.1")
                                    .status(PostStatus.DRAFT)
                                    .build());

            postRepository.flush();

            // Offset 목록 조회 검증
            PostSearchRequest offsetReq =
                    new PostSearchRequest("FREE", null, null, null, SortType.LATEST, 1, null, 10);
            var offsetResult = postService.searchPostsByOffset(offsetReq);
            List<String> offsetTitles =
                    offsetResult.content().stream()
                            .map(com.ikae.snowthing.domain.post.dto.PostListResponse::title)
                            .toList();

            assertThat(offsetTitles).contains("정상 공개 게시글");
            assertThat(offsetTitles).doesNotContain("숨김 게시글", "차단 게시글", "임시저장 게시글");

            // Cursor 목록 조회 검증
            PostSearchRequest cursorReq =
                    new PostSearchRequest(
                            "FREE", null, null, null, SortType.LATEST, null, null, 10);
            var cursorResult = postService.searchPostsByCursor(cursorReq);
            List<String> cursorTitles =
                    cursorResult.content().stream()
                            .map(com.ikae.snowthing.domain.post.dto.PostListResponse::title)
                            .toList();

            assertThat(cursorTitles).contains("정상 공개 게시글");
            assertThat(cursorTitles).doesNotContain("숨김 게시글", "차단 게시글", "임시저장 게시글");
        }

        @Test
        @DisplayName(
                "HIDDEN, BLOCKED, DRAFT 게시글 상세 조회, 추천, 댓글 작성 및 조회 시 일반 사용자는 404 POST_NOT_FOUND 예외가 발생한다.")
        void nonNormalPost_accessPolicy_throwsPostNotFound() {
            Post hiddenPost =
                    postRepository.save(
                            Post.builder()
                                    .member(member1)
                                    .category(freeCategory)
                                    .title("숨김 글")
                                    .content("숨김 본문")
                                    .writerIp("127.0.0.1")
                                    .status(PostStatus.HIDDEN)
                                    .build());

            // 1. 일반 사용자 상세 조회 -> 404
            assertThatThrownBy(
                            () ->
                                    postService.getPostDetail(
                                            hiddenPost.getPublicId(), userDetails2, false))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);

            // 2. 관리자 상세 조회 -> 허용
            Member admin =
                    memberRepository.save(
                            Member.builder()
                                    .email("admin_status@snowthing.com")
                                    .password(passwordEncoder.encode("Password123!"))
                                    .nickname("상태관리자")
                                    .role(Role.ROLE_ADMIN)
                                    .build());
            CustomUserDetails adminDetails = new CustomUserDetails(admin);
            var adminDetail =
                    postService.getPostDetail(hiddenPost.getPublicId(), adminDetails, false);
            assertThat(adminDetail.title()).isEqualTo("숨김 글");

            // 3. 추천 시도 -> 404
            assertThatThrownBy(
                            () ->
                                    postService.reactToPost(
                                            hiddenPost.getPublicId(),
                                            ReactionType.LIKE,
                                            userDetails2,
                                            "127.0.0.1",
                                            null))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);

            // 4. 댓글 작성 시도 -> 404
            assertThatThrownBy(
                            () ->
                                    commentService.createComment(
                                            hiddenPost.getPublicId(),
                                            com.ikae.snowthing.domain.comment.dto
                                                    .CommentCreateRequest.builder()
                                                    .content("숨김글에 댓글달기")
                                                    .isAnonymous(false)
                                                    .build(),
                                            userDetails2,
                                            "127.0.0.1"))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);

            // 5. 댓글 목록 조회 시도 -> 404
            assertThatThrownBy(() -> commentService.getCommentsByPost(hiddenPost.getPublicId()))
                    .isInstanceOf(CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("수정일시(updated_at) 고스트 업데이트 방지 및 본문 수정 시 갱신 정책 테스트")
    class PostUpdatedAtGhostUpdatePolicyTest {

        @Test
        @DisplayName("게시글 조회수(view_count) 및 추천수(like_count) 증감 시에는 updated_at이 절대 변경되지 않는다.")
        void countUpdates_doNotChangeUpdatedAt() {
            // 1. 게시글 생성
            PostResponse created =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("고스트 업데이트 방지 테스트")
                                    .content("원문 본문")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            entityManager.flush();
            entityManager.clear();

            Post initialPost = postRepository.findByPublicId(created.publicId()).orElseThrow();
            java.time.LocalDateTime initialUpdatedAt = initialPost.getUpdatedAt();

            // 2. 조회수 증가 발생
            postService.getPostDetail(created.publicId(), userDetails1, true);

            // 3. 추천수 증가 발생
            postService.reactToPost(
                    created.publicId(), ReactionType.LIKE, userDetails2, "127.0.0.1", null);

            entityManager.flush();
            entityManager.clear();

            // 4. DB 검증: view_count, like_count는 올랐지만 updated_at은 생성 시점 그대로 유지
            Post verified = postRepository.findByPublicId(created.publicId()).orElseThrow();
            assertThat(verified.getViewCount()).isEqualTo(1);
            assertThat(verified.getLikeCount()).isEqualTo(1);
            assertThat(verified.getUpdatedAt()).isEqualTo(initialUpdatedAt);
        }

        @Test
        @DisplayName("사용자가 실제 본문/제목을 수정(updatePost)한 경우에만 updated_at이 최신 시점으로 갱신된다.")
        void updatePost_updatesUpdatedAtTimestamp() throws Exception {
            // 1. 게시글 생성
            PostResponse created =
                    postService.createPost(
                            PostCreateRequest.builder()
                                    .categoryCode("FREE")
                                    .title("수정 전 제목")
                                    .content("수정 전 본문")
                                    .isAnonymous(false)
                                    .build(),
                            userDetails1,
                            "127.0.0.1");

            entityManager.flush();
            entityManager.clear();

            Post initialPost = postRepository.findByPublicId(created.publicId()).orElseThrow();
            java.time.LocalDateTime initialUpdatedAt = initialPost.getUpdatedAt();

            Thread.sleep(50); // 시간차 보장

            // 2. 실제 본문 수정
            postService.updatePost(
                    created.publicId(),
                    PostUpdateRequest.builder()
                            .categoryCode("FREE")
                            .title("수정된 제목")
                            .content("수정된 본문")
                            .build(),
                    userDetails1);

            entityManager.flush();
            entityManager.clear();

            // 3. DB 검증: updated_at이 이전 시점보다 이후(after)로 갱신됨
            Post updated = postRepository.findByPublicId(created.publicId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("수정된 제목");
            assertThat(updated.getUpdatedAt()).isAfter(initialUpdatedAt);
        }
    }
}
