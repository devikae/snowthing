package com.ikae.snowthing.domain.post.service;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCategoryRepository categoryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PostReactionRepository reactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member member1;
    private Member member2;
    private CustomUserDetails userDetails1;
    private CustomUserDetails userDetails2;
    private PostCategory freeCategory;

    @BeforeEach
    void setUp() {
        member1 = memberRepository.save(Member.builder()
            .email("user1@example.com")
            .password(passwordEncoder.encode("Password123!"))
            .nickname("보더1호")
            .role(Role.ROLE_USER)
            .build());

        member2 = memberRepository.save(Member.builder()
            .email("user2@example.com")
            .password(passwordEncoder.encode("Password123!"))
            .nickname("보더2호")
            .role(Role.ROLE_USER)
            .build());

        userDetails1 = new CustomUserDetails(member1);
        userDetails2 = new CustomUserDetails(member2);

        freeCategory = categoryRepository.findByCode("FREE")
            .orElseGet(() -> categoryRepository.save(PostCategory.builder().name("자유게시판").code("FREE").build()));
    }

    @Nested
    @DisplayName("게시글 작성 테스트")
    class CreatePostTest {

        @Test
        @DisplayName("로그인 회원이 정상적으로 게시글을 작성한다.")
        void createPost_success_member() {
            PostCreateRequest request = PostCreateRequest.builder()
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
        }

        @Test
        @DisplayName("비회원이 익명 게시글을 정상적으로 작성한다.")
        void createPost_success_anonymous() {
            PostCreateRequest request = PostCreateRequest.builder()
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
            PostCreateRequest request = PostCreateRequest.builder()
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
            PostCreateRequest request = PostCreateRequest.builder()
                .categoryCode("FREE")
                .title("조회수 테스트")
                .content("상세 내용")
                .isAnonymous(false)
                .build();

            PostResponse created = postService.createPost(request, userDetails1, "127.0.0.1");

            PostDetailResponse detail = postService.getPostDetail(created.publicId(), userDetails1);

            assertThat(detail.title()).isEqualTo("조회수 테스트");
            assertThat(detail.viewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("존재하지 않는 publicId 조회 시 404 예외가 터진다.")
        void getPostDetail_notFound() {
            assertThatThrownBy(() -> postService.getPostDetail("non-existent-uuid", userDetails1))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("목록 조회 시 본문(content)이 제외되고 최신순/id역순으로 페이징 조회된다.")
        void getPostList_success() {
            for (int i = 1; i <= 5; i++) {
                postService.createPost(PostCreateRequest.builder()
                    .categoryCode("FREE")
                    .title("테스트 글 " + i)
                    .content("본문 내용 " + i)
                    .isAnonymous(false)
                    .build(), userDetails1, "127.0.0.1");
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
            createdPost = postService.createPost(PostCreateRequest.builder()
                .categoryCode("FREE")
                .title("수정전 제목")
                .content("수정전 본문")
                .isAnonymous(false)
                .build(), userDetails1, "127.0.0.1");
        }

        @Test
        @DisplayName("작성자 본인은 게시글을 정상 수정한다.")
        void updatePost_success_writer() {
            PostUpdateRequest updateReq = PostUpdateRequest.builder()
                .categoryCode("FREE")
                .title("수정후 제목")
                .content("수정후 본문")
                .build();

            PostResponse updated = postService.updatePost(createdPost.publicId(), updateReq, userDetails1);

            assertThat(updated.title()).isEqualTo("수정후 제목");
        }

        @Test
        @DisplayName("타 회원이 수정 시도 시 403 Forbidden 예외가 터진다.")
        void updatePost_forbidden_otherUser() {
            PostUpdateRequest updateReq = PostUpdateRequest.builder()
                .categoryCode("FREE")
                .title("타인 해킹 시도")
                .content("해킹 본문")
                .build();

            assertThatThrownBy(() -> postService.updatePost(createdPost.publicId(), updateReq, userDetails2))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("작성자 본인은 Soft Delete로 게시글을 정상 삭제한다.")
        void deletePost_success_softDelete() {
            postService.deletePost(createdPost.publicId(), null, userDetails1);

            assertThatThrownBy(() -> postService.getPostDetail(createdPost.publicId(), userDetails1))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 삭제된 게시글에 대해 삭제를 재시도하면 404 예외가 터진다.")
        void deletePost_alreadyDeleted() {
            postService.deletePost(createdPost.publicId(), null, userDetails1);

            assertThatThrownBy(() -> postService.deletePost(createdPost.publicId(), null, userDetails1))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("추천/비추천 중복 제약 조건 테스트")
    class ReactionTest {

        @Test
        @DisplayName("동일 회원 중복 추천 시 DB UNIQUE 제약 조건에 걸려 ALREADY_REACTED 예외가 터진다.")
        void reactToPost_duplicate_throwsAlreadyReacted() {
            PostResponse post = postService.createPost(PostCreateRequest.builder()
                .categoryCode("FREE")
                .title("추천 테스트 글")
                .content("내용")
                .isAnonymous(false)
                .build(), userDetails1, "127.0.0.1");

            postService.reactToPost(post.publicId(), ReactionType.LIKE, userDetails1);

            assertThatThrownBy(() -> postService.reactToPost(post.publicId(), ReactionType.LIKE, userDetails1))
                .isInstanceOf(CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_REACTED);
        }
    }
}
