package com.ikae.snowthing.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import com.ikae.snowthing.domain.post.dto.ScrapPostResponse;
import com.ikae.snowthing.domain.post.dto.ScrapResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 스크랩은 멱등하고, 목록은 최근 스크랩 순이며 볼 수 없는 글은 빠진다. */
@SpringBootTest
@Transactional
class PostScrapServiceTest {

    @Autowired private PostScrapService scraps;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbc;

    private CustomUserDetails alice;
    private CustomUserDetails bob;
    private Member writer;

    @BeforeEach
    void setUp() {
        for (String code : List.of("FREE", "INQUIRY", "MARKET", "CARPOOL")) {
            categoryRepository
                    .findByCode(code)
                    .orElseGet(() -> categoryRepository.save(new PostCategory(code, code)));
        }
        alice = new CustomUserDetails(saveMember("scrap-alice"));
        bob = new CustomUserDetails(saveMember("scrap-bob"));
        writer = saveMember("scrap-writer");
    }

    @Test
    @DisplayName("같은 글을 두 번 스크랩해도 한 줄만 남고 두 번째는 changed=false다")
    void scrapIsIdempotent() {
        Post post = savePost("FREE", "장비 리뷰");

        assertThat(scraps.scrap(post.getPublicId(), alice))
                .isEqualTo(new ScrapResponse(true, true));
        assertThat(scraps.scrap(post.getPublicId(), alice))
                .isEqualTo(new ScrapResponse(true, false));

        assertThat(countScraps(alice, post)).isEqualTo(1L);
        assertThat(scraps.isScrapped(post.getPublicId(), alice)).isTrue();
        assertThat(scraps.isScrapped(post.getPublicId(), bob)).isFalse();
    }

    @Test
    @DisplayName("취소는 스크랩하지 않았거나 없는 글이어도 성공한다")
    void unscrapIsIdempotent() {
        Post post = savePost("FREE", "강습 정보");
        scraps.scrap(post.getPublicId(), alice);
        scraps.scrap(post.getPublicId(), bob);

        assertThat(scraps.unscrap(post.getPublicId(), alice))
                .isEqualTo(new ScrapResponse(false, true));
        assertThat(scraps.unscrap(post.getPublicId(), alice))
                .isEqualTo(new ScrapResponse(false, false));
        assertThat(scraps.unscrap(UUID.randomUUID().toString(), alice))
                .isEqualTo(new ScrapResponse(false, false));

        assertThat(countScraps(alice, post)).isZero();
        // 다른 회원의 스크랩은 건드리지 않는다.
        assertThat(countScraps(bob, post)).isEqualTo(1L);
    }

    @Test
    @DisplayName("비로그인과 봇 위임 요청은 401이고 상세 scrapped는 false다")
    void loginRequired() {
        Post post = savePost("FREE", "글");
        CustomUserDetails bot = new BotDelegatedUserDetails(alice.getMember());

        assertThatThrownBy(() -> scraps.scrap(post.getPublicId(), null))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCRAP_LOGIN_REQUIRED);
        assertThatThrownBy(() -> scraps.unscrap(post.getPublicId(), null))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCRAP_LOGIN_REQUIRED);
        assertThatThrownBy(() -> scraps.getMyScraps(null, null, null))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCRAP_LOGIN_REQUIRED);
        assertThatThrownBy(() -> scraps.scrap(post.getPublicId(), bot))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCRAP_LOGIN_REQUIRED);
        assertThat(scraps.isScrapped(post.getPublicId(), null)).isFalse();
    }

    @Test
    @DisplayName("없는 글, 삭제, 임시조치, 1:1 문의는 404이고 판매글과 카풀은 400이다")
    void invalidTargets() {
        Post deleted = savePost("FREE", "삭제");
        deleted.softDelete();
        Post held = savePost("FREE", "임시조치");
        held.changeStatus(PostStatus.TEMPORARY_HOLD);
        postRepository.flush();

        for (String publicId :
                List.of(
                        UUID.randomUUID().toString(),
                        deleted.getPublicId(),
                        held.getPublicId(),
                        savePost("INQUIRY", "문의").getPublicId())) {
            assertThatThrownBy(() -> scraps.scrap(publicId, alice))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.POST_NOT_FOUND);
        }
        for (String code : List.of("MARKET", "CARPOOL")) {
            String publicId = savePost(code, code).getPublicId();
            assertThatThrownBy(() -> scraps.scrap(publicId, alice))
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SCRAP_NOT_ALLOWED);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post_scrap WHERE member_id = ?",
                                Long.class,
                                alice.getMember().getId()))
                .isZero();
    }

    @Test
    @DisplayName("목록은 최근 스크랩 순, 같은 시각은 ID 내림차순이고 커서로 이어 읽는다")
    void listOrderAndCursor() {
        Post first = savePost("FREE", "첫째");
        Post second = savePost("FREE", "둘째");
        Post third = savePost("FREE", "셋째");
        Post older = savePost("FREE", "예전");
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 9, 22, 0, 0, 123_456_000);
        insertScrap(alice, older, sameTime.minusDays(1));
        insertScrap(alice, first, sameTime);
        insertScrap(alice, second, sameTime);
        insertScrap(alice, third, sameTime);
        insertScrap(bob, first, sameTime.plusDays(1));

        CursorPageResponse<ScrapPostResponse> page1 = scraps.getMyScraps(alice, null, 2);
        assertThat(page1.content())
                .extracting(ScrapPostResponse::postPublicId)
                .containsExactly(third.getPublicId(), second.getPublicId());
        assertThat(page1.pageInfo().hasNext()).isTrue();

        CursorPageResponse<ScrapPostResponse> page2 =
                scraps.getMyScraps(alice, page1.pageInfo().nextCursor(), 2);
        assertThat(page2.content())
                .extracting(ScrapPostResponse::postPublicId)
                .containsExactly(first.getPublicId(), older.getPublicId());
        assertThat(page2.pageInfo().hasNext()).isFalse();
        assertThat(page2.pageInfo().nextCursor()).isNull();

        ScrapPostResponse row = page2.content().get(0);
        assertThat(row.title()).isEqualTo("첫째");
        assertThat(row.categoryCode()).isEqualTo("FREE");
        assertThat(row.writerNickname()).isEqualTo(writer.getNickname());
        // JdbcTemplate로 넣은 값은 테스트 JVM 시간대(UTC) 변환을 거치므로 값 대신 존재만 본다. 순서와 커서는 위에서 확인했다.
        assertThat(row.scrappedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제되거나 임시조치된 글은 목록에서 빠지고 복구되면 다시 보인다")
    void hiddenPostsExcluded() {
        Post visible = savePost("FREE", "보이는 글");
        Post deleted = savePost("FREE", "지운 글");
        Post held = savePost("FREE", "가린 글");
        scraps.scrap(visible.getPublicId(), alice);
        scraps.scrap(deleted.getPublicId(), alice);
        scraps.scrap(held.getPublicId(), alice);

        jdbc.update(
                "UPDATE post SET is_deleted = TRUE, status = 'DELETED' WHERE post_id = ?",
                deleted.getId());
        jdbc.update("UPDATE post SET status = 'TEMPORARY_HOLD' WHERE post_id = ?", held.getId());

        assertThat(scraps.getMyScraps(alice, null, null).content())
                .extracting(ScrapPostResponse::postPublicId)
                .containsExactly(visible.getPublicId());

        jdbc.update("UPDATE post SET status = 'NORMAL' WHERE post_id = ?", held.getId());
        assertThat(scraps.getMyScraps(alice, null, null).content())
                .extracting(ScrapPostResponse::postPublicId)
                .containsExactlyInAnyOrder(visible.getPublicId(), held.getPublicId());
    }

    @Test
    @DisplayName("페이지 크기는 1~50으로 맞추고 잘못된 커서는 처음부터 읽는다")
    void sizeAndCursorBoundaries() {
        Post post = savePost("FREE", "글");
        scraps.scrap(post.getPublicId(), alice);

        assertThat(scraps.getMyScraps(alice, null, 0).pageInfo().pageSize())
                .isEqualTo(PostScrapService.DEFAULT_SIZE);
        assertThat(scraps.getMyScraps(alice, null, 1000).pageInfo().pageSize())
                .isEqualTo(PostScrapService.MAX_SIZE);
        assertThat(scraps.getMyScraps(alice, "not-a-cursor", null).content()).hasSize(1);
        assertThat(scraps.getMyScraps(bob, null, null).content()).isEmpty();
    }

    private void insertScrap(CustomUserDetails member, Post post, LocalDateTime at) {
        jdbc.update(
                "INSERT INTO post_scrap (member_id, post_id, created_at) VALUES (?, ?, ?)",
                member.getMember().getId(),
                post.getId(),
                at);
    }

    private long countScraps(CustomUserDetails member, Post post) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM post_scrap WHERE member_id = ? AND post_id = ?",
                Long.class,
                member.getMember().getId(),
                post.getId());
    }

    private Post savePost(String categoryCode, String title) {
        return postRepository.saveAndFlush(
                Post.builder()
                        .member(writer)
                        .category(categoryRepository.findByCode(categoryCode).orElseThrow())
                        .title(title)
                        .content("본문")
                        .writerIp("127.0.0.1")
                        .build());
    }

    private Member saveMember(String prefix) {
        String unique = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(
                Member.builder()
                        .email(unique + "@example.com")
                        .nickname(unique)
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build());
    }
}
