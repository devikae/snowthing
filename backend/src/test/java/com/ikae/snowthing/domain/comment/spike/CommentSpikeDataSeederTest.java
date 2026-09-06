package com.ikae.snowthing.domain.comment.spike;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;

import lombok.extern.slf4j.Slf4j;

/** 실제 로컬 MySQL DB에 Spike용 1,000건 데이터를 직접 생성하고 영속화(Commit)하는 Seeder 러너 */
@Slf4j
@Disabled("전체 빌드 시 자동 실행 방지 (Spike 데이터 주입 시에만 수동 실행)")
@SpringBootTest
public class CommentSpikeDataSeederTest {

    @Autowired private MemberRepository memberRepository;

    @Autowired private PostCategoryRepository postCategoryRepository;

    @Autowired private PostRepository postRepository;

    @Autowired private CommentSpikeDataInitializer dataInitializer;

    @Test
    @DisplayName("실제 DB에 Spike 시나리오 A(분산 1,000건) & 시나리오 B(핫스팟 1,000건) 데이터 생성")
    @Transactional
    @Rollback(false) // 실제 DB에 영구 커밋
    void seedAllSpikeData() {
        // 1. 기본 회원 생성
        Member member =
                memberRepository
                        .findByEmail("spike@snowthing.com")
                        .orElseGet(
                                () ->
                                        memberRepository.save(
                                                Member.builder()
                                                        .email("spike@snowthing.com")
                                                        .password(
                                                                "$2a$10$dummyHashValueForSpikeTestingOnly1234567890")
                                                        .nickname("스파이크테스터")
                                                        .role(Role.ROLE_USER)
                                                        .status(MemberStatus.ACTIVE)
                                                        .build()));

        // 2. 기본 카테고리 생성
        PostCategory category =
                postCategoryRepository
                        .findByCode("FREE")
                        .orElseGet(
                                () ->
                                        postCategoryRepository.save(
                                                PostCategory.builder()
                                                        .name("자유게시판")
                                                        .code("FREE")
                                                        .build()));

        // 3. Post 998 (분산 1,000건용 게시글)
        Post postDistributed =
                postRepository
                        .findByPublicId("post-spike-distributed-998")
                        .orElseGet(
                                () ->
                                        postRepository.save(
                                                Post.builder()
                                                        .member(member)
                                                        .category(category)
                                                        .title("Spike [시나리오 A] 분산 1,000건 테스트 글")
                                                        .content("내용")
                                                        .writerIp("127.0.0.1")
                                                        .isAnonymous(false)
                                                        .build()));

        // 4. Post 999 (집중 1,000건용 게시글)
        Post postHotspot =
                postRepository
                        .findByPublicId("post-spike-hotspot-999")
                        .orElseGet(
                                () ->
                                        postRepository.save(
                                                Post.builder()
                                                        .member(member)
                                                        .category(category)
                                                        .title("Spike [시나리오 B] 핫스팟 500건 집중 테스트 글")
                                                        .content("내용")
                                                        .writerIp("127.0.0.1")
                                                        .isAnonymous(false)
                                                        .build()));

        log.info("========== Spike 데이터 삽입 시작 ==========");
        dataInitializer.setupDistributedScenario(postDistributed, member);
        log.info(
                "[완료] 시나리오 A (Post ID: {}, PublicId: {}) - 분산 1,000건 생성 완료",
                postDistributed.getId(),
                postDistributed.getPublicId());

        dataInitializer.setupHotspotScenario(postHotspot, member);
        log.info(
                "[완료] 시나리오 B (Post ID: {}, PublicId: {}) - 핫스팟 1,000건 생성 완료",
                postHotspot.getId(),
                postHotspot.getPublicId());
        log.info("========== Spike 데이터 삽입 종료 ==========");
    }
}
