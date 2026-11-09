package com.ikae.snowthing.domain.post.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 글 스크랩 (042). 설계: docs/conception/026_post_scrap/01_design.md
 *
 * <p>행은 {@code PostScrapRepository.insertIgnoringDuplicate}로만 넣는다. UNIQUE (member_id, post_id) 위반을
 * 예외로 받으면 트랜잭션이 롤백 전용이 되므로, 중복 스크랩은 DB의 ON DUPLICATE KEY로 조용히 넘긴다.
 */
@Entity
@Table(
        name = "post_scrap",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_post_scrap_member_post",
                        columnNames = {"member_id", "post_id"}),
        indexes = {
            @Index(
                    name = "idx_post_scrap_member_latest",
                    columnList = "member_id, created_at DESC, post_scrap_id DESC"),
            @Index(name = "idx_post_scrap_post", columnList = "post_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostScrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_scrap_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
