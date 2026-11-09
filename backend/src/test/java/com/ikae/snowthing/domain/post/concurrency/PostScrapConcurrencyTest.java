package com.ikae.snowthing.domain.post.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.ScrapResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostScrapService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 같은 회원이 같은 글을 동시에 여러 번 스크랩해도 UNIQUE 위반 500 없이 한 줄만 남는다. */
@SpringBootTest
class PostScrapConcurrencyTest {

    private static final int REQUESTS = 8;

    @Autowired private PostScrapService scraps;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long memberId;
    private Long postId;

    @AfterEach
    void tearDown() {
        if (postId != null) {
            jdbc.update("DELETE FROM post_scrap WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        if (memberId != null) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("동시 스크랩 요청은 모두 성공하고 행은 하나, 상태 변경은 한 번이다")
    void concurrentScrapLeavesOneRow() throws Exception {
        PostCategory free =
                categoryRepository
                        .findByCode("FREE")
                        .orElseGet(() -> categoryRepository.save(new PostCategory("FREE", "FREE")));
        String unique = "scrap-race-" + UUID.randomUUID().toString().substring(0, 8);
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email(unique + "@example.com")
                                .nickname(unique)
                                .role(Role.ROLE_USER)
                                .build());
        memberId = member.getId();
        Post post =
                postRepository.save(
                        Post.builder()
                                .member(member)
                                .category(free)
                                .title("동시 스크랩")
                                .content("본문")
                                .writerIp("127.0.0.1")
                                .build());
        postId = post.getId();
        CustomUserDetails principal = new CustomUserDetails(member);

        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ScrapResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < REQUESTS; i++) {
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return scraps.scrap(post.getPublicId(), principal);
                                }));
            }
            start.countDown();
            long changed = 0;
            for (Future<ScrapResponse> result : results) {
                ScrapResponse response = result.get();
                assertThat(response.scrapped()).isTrue();
                if (response.changed()) changed++;
            }
            assertThat(changed).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM post_scrap WHERE member_id = ? AND post_id = ?",
                                Long.class,
                                memberId,
                                postId))
                .isEqualTo(1L);
    }
}
