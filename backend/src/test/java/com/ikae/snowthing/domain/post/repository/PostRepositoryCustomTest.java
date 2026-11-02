package com.ikae.snowthing.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.domain.post.dto.PostViewType;
import com.ikae.snowthing.domain.post.dto.SearchType;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;

@SpringBootTest
@Transactional
class PostRepositoryCustomTest {

    @Autowired private PostRepository postRepository;

    @Autowired private PostCategoryRepository categoryRepository;

    @Autowired private MemberRepository memberRepository;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private EntityManager entityManager;

    private PostCategory freeCategory;
    private Member testMember;

    @BeforeEach
    void setUp() {
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

        testMember =
                memberRepository.save(
                        Member.builder()
                                .publicId(UUID.randomUUID().toString())
                                .email("boarder@snow.com")
                                .nickname("카빙마스터")
                                .role(Role.ROLE_USER)
                                .status(MemberStatus.ACTIVE)
                                .build());

        // 테스트 데이터 25개 저장
        for (int i = 1; i <= 25; i++) {
            Post post =
                    Post.builder()
                            .member(testMember)
                            .category(freeCategory)
                            .title("휘닉스파크 설질 제보 " + i)
                            .content("오늘 챔피언 슬로프 상태 아주 좋습니다 " + i)
                            .writerIp("127.0.0.1")
                            .isAnonymous(false)
                            .build();
            postRepository.save(post);
        }
    }

    @Test
    @DisplayName("웹 Offset 기반 페이징 조회 - 1페이지 (size=10)")
    void findPostsByOffset_firstPage() {
        // given
        PostSearchRequest request =
                new PostSearchRequest("FREE", null, null, null, PostViewType.DEFAULT, 1, null, 10);

        // when
        CursorPageResponse<PostListResponse> response = postRepository.findPostsByOffset(request);

        // then
        assertThat(response.content()).hasSize(10);
        assertThat(response.pageInfo().page()).isEqualTo(1);
        assertThat(response.pageInfo().totalPages()).isEqualTo(3);
        assertThat(response.pageInfo().totalElements()).isEqualTo(25L);
        assertThat(response.pageInfo().hasNext()).isTrue();
    }

    @Test
    @DisplayName("모바일 Keyset Cursor 기반 페이징 조회 - 첫 페이지 (size=10) 및 nextCursor 획득")
    void findPostsByCursor_firstPage() {
        // given
        PostSearchRequest request =
                new PostSearchRequest(
                        "FREE", null, null, null, PostViewType.DEFAULT, null, null, 10);

        // when
        CursorPageResponse<PostListResponse> response = postRepository.findPostsByCursor(request);

        // then
        assertThat(response.content()).hasSize(10);
        assertThat(response.pageInfo().hasNext()).isTrue();
        assertThat(response.pageInfo().nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("동적 키워드 검색 (TITLE) - '설질' 포함 25건 조회")
    void findPostsBySearch_titleKeyword() {
        // given
        PostSearchRequest request =
                new PostSearchRequest(
                        "FREE", null, SearchType.TITLE, "설질", PostViewType.DEFAULT, 1, null, 10);

        // when
        CursorPageResponse<PostListResponse> response = postRepository.findPostsByOffset(request);

        // then
        assertThat(response.content()).hasSize(10);
        assertThat(response.pageInfo().totalElements()).isEqualTo(25L);
    }

    @Test
    @DisplayName("전체 베스트는 추천 30 이상인 모든 카테고리 글을 최신순으로 반환한다")
    void findPostsByPopular_filtersByCutoffAndSortsLatest() {
        // given
        PostCategory anonymousCategory =
                categoryRepository
                        .findByCode("ANONYMOUS")
                        .orElseGet(
                                () ->
                                        categoryRepository.save(
                                                PostCategory.builder()
                                                        .name("익명게시판")
                                                        .code("ANONYMOUS")
                                                        .build()));
        PostCategory qnaCategory =
                categoryRepository
                        .findByCode("QNA")
                        .orElseGet(
                                () ->
                                        categoryRepository.save(
                                                PostCategory.builder()
                                                        .name("질문게시판")
                                                        .code("QNA")
                                                        .build()));
        Post qualifyingFreePost =
                Post.builder()
                        .member(testMember)
                        .category(freeCategory)
                        .title("추천 30 자유게시판 글")
                        .content("베스트 기준 충족")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();
        increaseLikeCount(qualifyingFreePost, 30);
        postRepository.save(qualifyingFreePost);

        Post qualifyingAnonymousPost =
                Post.builder()
                        .category(anonymousCategory)
                        .title("추천 30 익명게시판 글")
                        .content("전체 베스트 카테고리 통합 검증")
                        .writerIp("127.0.0.2")
                        .isAnonymous(true)
                        .anonymousPassword("Password1!")
                        .build();
        increaseLikeCount(qualifyingAnonymousPost, 30);
        postRepository.save(qualifyingAnonymousPost);

        Post belowCutoffPost =
                Post.builder()
                        .member(testMember)
                        .category(qnaCategory)
                        .title("추천 29 질문게시판 글")
                        .content("베스트 기준 미달")
                        .writerIp("127.0.0.1")
                        .isAnonymous(false)
                        .build();
        increaseLikeCount(belowCutoffPost, 29);
        postRepository.save(belowCutoffPost);

        PostSearchRequest offsetRequest =
                new PostSearchRequest(null, null, null, null, PostViewType.BEST, 1, null, 10);
        PostSearchRequest cursorRequest =
                new PostSearchRequest(null, null, null, null, PostViewType.BEST, null, null, 10);

        // when
        CursorPageResponse<PostListResponse> offsetResponse =
                postRepository.findPostsByOffset(offsetRequest);
        CursorPageResponse<PostListResponse> cursorResponse =
                postRepository.findPostsByCursor(cursorRequest);

        // then
        assertThat(offsetResponse.content())
                .extracting(PostListResponse::publicId)
                .containsExactly(
                        qualifyingAnonymousPost.getPublicId(), qualifyingFreePost.getPublicId());
        assertThat(offsetResponse.content())
                .extracting(PostListResponse::categoryCode)
                .containsExactly("ANONYMOUS", "FREE");
        assertThat(offsetResponse.content()).allMatch(post -> post.likeCount() >= 30);
        assertThat(offsetResponse.pageInfo().totalElements()).isEqualTo(2L);
        assertThat(cursorResponse.content())
                .extracting(PostListResponse::publicId)
                .containsExactly(
                        qualifyingAnonymousPost.getPublicId(), qualifyingFreePost.getPublicId());
    }

    @Test
    @DisplayName("전체글은 게시판별 삽입 순서와 무관하게 작성 시각 최신순으로 페이지를 반환한다")
    void latestOffsetUsesCreationTimeAcrossCategories() {
        List<String> expected = createOutOfOrderSeedPosts();
        List<String> actual = new ArrayList<>();

        for (int page = 1; page <= 3; page++) {
            var response = postRepository.findPostsByOffset(seedSearch(null, page, null, 2));
            assertThat(response.pageInfo().totalElements()).isEqualTo(6);
            actual.addAll(response.content().stream().map(PostListResponse::publicId).toList());
        }

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("작성 시각이 ID 순서와 달라도 커서 페이지는 같은 시각의 글까지 누락·중복 없이 반환한다")
    void latestCursorUsesCreationTimeAndIdWithoutSkipping() {
        List<String> expected = createOutOfOrderSeedPosts();
        List<String> actual = new ArrayList<>();
        String cursor = null;

        for (int index = 0; index < expected.size(); index++) {
            var response = postRepository.findPostsByCursor(seedSearch(null, null, cursor, 1));
            assertThat(response.content()).hasSize(1);
            actual.add(response.content().getFirst().publicId());
            assertThat(response.pageInfo().hasNext()).isEqualTo(index < expected.size() - 1);
            cursor = response.pageInfo().nextCursor();
        }

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("개별 게시판과 베스트 조회도 동일한 작성 시각 최신순을 사용한다")
    void categoryAndBestUseCreationTime() {
        List<String> expected = createOutOfOrderSeedPosts();
        var free = postRepository.findPostsByOffset(seedSearch("FREE", 1, null, 10));
        assertThat(free.content())
                .extracting(PostListResponse::publicId)
                .containsExactly(expected.get(1), expected.get(5));

        var request =
                new PostSearchRequest(
                        null,
                        null,
                        SearchType.TITLE,
                        "seed-sort-regression",
                        PostViewType.BEST,
                        1,
                        null,
                        10);
        assertThat(postRepository.findPostsByOffset(request).content())
                .extracting(PostListResponse::publicId)
                .containsExactlyElementsOf(expected);
        assertThat(postRepository.findPostsByCursor(request).content())
                .extracting(PostListResponse::publicId)
                .containsExactlyElementsOf(expected);
    }

    private PostSearchRequest seedSearch(String category, Integer page, String cursor, int size) {
        return new PostSearchRequest(
                category,
                null,
                SearchType.TITLE,
                "seed-sort-regression",
                PostViewType.DEFAULT,
                page,
                cursor,
                size);
    }

    private List<String> createOutOfOrderSeedPosts() {
        String[] categories = {"FREE", "FREE", "ANONYMOUS", "ANONYMOUS", "QNA", "QNA"};
        LocalDateTime[] times = {
            LocalDateTime.of(2026, 10, 26, 10, 0),
            LocalDateTime.of(2026, 11, 2, 12, 0),
            LocalDateTime.of(2026, 11, 1, 10, 0),
            LocalDateTime.of(2026, 11, 2, 12, 0),
            LocalDateTime.of(2026, 10, 27, 10, 0),
            LocalDateTime.of(2026, 10, 30, 10, 0)
        };
        List<Post> seeded = new ArrayList<>();
        for (int index = 0; index < categories.length; index++) {
            String code = categories[index];
            PostCategory category =
                    categoryRepository
                            .findByCode(code)
                            .orElseGet(
                                    () ->
                                            categoryRepository.save(
                                                    PostCategory.builder()
                                                            .code(code)
                                                            .name(code)
                                                            .build()));
            Post post =
                    Post.builder()
                            .member(testMember)
                            .category(category)
                            .title("seed-sort-regression " + index)
                            .content("backdated CSV seed")
                            .writerIp("192.0.2.0")
                            .isAnonymous("ANONYMOUS".equals(code))
                            .build();
            increaseLikeCount(post, 30);
            postRepository.saveAndFlush(post);
            jdbcTemplate.update(
                    "UPDATE post SET created_at = ? WHERE post_id = ?", times[index], post.getId());
            seeded.add(post);
        }
        List<String> expected =
                List.of(
                        seeded.get(3).getPublicId(),
                        seeded.get(1).getPublicId(),
                        seeded.get(2).getPublicId(),
                        seeded.get(5).getPublicId(),
                        seeded.get(4).getPublicId(),
                        seeded.get(0).getPublicId());
        entityManager.clear();
        return expected;
    }

    private void increaseLikeCount(Post post, int count) {
        for (int i = 0; i < count; i++) {
            post.increaseLikeCount();
        }
    }
}
