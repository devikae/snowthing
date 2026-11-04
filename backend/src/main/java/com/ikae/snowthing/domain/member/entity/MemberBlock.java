package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 차단 (034). 쪽지 전용이 아니라 같이타요, 카풀에서도 재사용한다.
 *
 * <p>행은 {@code MemberBlockRepository.insertIgnoringDuplicate}로만 넣는다. UNIQUE (blocker_id,
 * blocked_id) 위반을 예외로 받으면 트랜잭션이 롤백 전용이 되므로, 중복 차단은 DB의 ON DUPLICATE KEY로 조용히 넘긴다.
 */
@Entity
@Table(
        name = "member_block",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_member_block_blocker_blocked",
                        columnNames = {"blocker_id", "blocked_id"}),
        indexes =
                @Index(
                        name = "idx_member_block_blocked_blocker",
                        columnList = "blocked_id, blocker_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_block_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private Member blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private Member blocked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
