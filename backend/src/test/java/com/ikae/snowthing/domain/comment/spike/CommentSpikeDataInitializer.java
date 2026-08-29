package com.ikae.snowthing.domain.comment.spike;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;

import lombok.RequiredArgsConstructor;

/** Spike 실험용 대용량 1,000건 댓글 데이터 생성기 */
@Component
@RequiredArgsConstructor
public class CommentSpikeDataInitializer {

    private final EntityManager em;

    /** [시나리오 A: 분산 1,000건] - 루트 댓글 100개 - 각 루트당 대댓글 9개씩 (100 * 9 = 900개) - 총 1,000개 */
    @Transactional
    public void setupDistributedScenario(Post post, Member member) {
        List<Comment> roots = new ArrayList<>();

        // 1. 루트 댓글 100개 생성
        for (int i = 1; i <= 100; i++) {
            Comment root =
                    Comment.builder()
                            .post(post)
                            .member(member)
                            .parent(null)
                            .content("루트 댓글 #" + i)
                            .writerIp("127.0.0.1")
                            .isAnonymous(false)
                            .build();
            em.persist(root);
            roots.add(root);

            if (i % 50 == 0) {
                em.flush();
            }
        }
        em.flush();

        // 2. 각 루트당 대댓글 9개씩 생성 (총 900개)
        int replySeq = 1;
        for (Comment root : roots) {
            for (int r = 1; r <= 9; r++) {
                Comment reply =
                        Comment.builder()
                                .post(post)
                                .member(member)
                                .parent(root)
                                .content("대댓글 #" + (replySeq++) + " (부모:" + root.getId() + ")")
                                .writerIp("127.0.0.1")
                                .isAnonymous(false)
                                .build();
                em.persist(reply);
            }
        }
        em.flush();
        em.clear();
    }

    /** [시나리오 B: 집중 핫스팟 1,000건] - 루트 댓글 500개 - 1번 루트 댓글 1개에 대댓글 500개 몰림 - 총 1,000개 */
    @Transactional
    public void setupHotspotScenario(Post post, Member member) {
        Comment hotspotRoot = null;

        // 1. 루트 댓글 500개 생성
        for (int i = 1; i <= 500; i++) {
            Comment root =
                    Comment.builder()
                            .post(post)
                            .member(member)
                            .parent(null)
                            .content("루트 댓글 #" + i)
                            .writerIp("127.0.0.1")
                            .isAnonymous(false)
                            .build();
            em.persist(root);
            if (i == 1) {
                hotspotRoot = root;
            }

            if (i % 50 == 0) {
                em.flush();
            }
        }
        em.flush();

        // 2. 1번 루트 댓글에 대댓글 500개 집중 생성
        for (int r = 1; r <= 500; r++) {
            Comment reply =
                    Comment.builder()
                            .post(post)
                            .member(member)
                            .parent(hotspotRoot)
                            .content("핫스팟 대댓글 #" + r)
                            .writerIp("127.0.0.1")
                            .isAnonymous(false)
                            .build();
            em.persist(reply);

            if (r % 50 == 0) {
                em.flush();
            }
        }
        em.flush();
        em.clear();
    }
}
