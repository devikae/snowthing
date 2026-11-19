package com.ikae.snowthing.domain.contentfilter.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 금칙어로 보류 중인 글이나 댓글 한 건 (IKA-96). 보류가 풀리거나(공개 전환, 깨끗한 내용으로 수정) 지워지면 줄을 지운다. 처리 이력은 관리자 조치 기록에 남는다.
 *
 * <p>공개 여부의 기준은 이 표가 아니라 원문 상태(post.status = FILTER_HOLD, comment.filter_state = HELD)다. 이 표는 관리자
 * 검토 목록과 비회원 작성자 확인(같은 IP, 보류 뒤 짧은 시간)에 쓰는 기록이다.
 */
@Entity
@Table(
        name = "content_filter_hold",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_content_filter_hold_target",
                        columnNames = {"target_type", "target_id"}),
        indexes = {
            @Index(
                    name = "idx_content_filter_hold_list",
                    columnList = "held_at,content_filter_hold_id"),
            @Index(name = "idx_content_filter_hold_member", columnList = "member_id"),
            @Index(name = "idx_content_filter_hold_ip", columnList = "writer_ip,held_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentFilterHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_filter_hold_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private FilterTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "writer_ip", nullable = false, length = 45)
    private String writerIp;

    @Column(name = "matched_word", nullable = false, length = BannedWord.MAX_LENGTH)
    private String matchedWord;

    @Column(name = "held_at", nullable = false)
    private LocalDateTime heldAt;

    public ContentFilterHold(
            FilterTargetType targetType,
            Long targetId,
            Long postId,
            Long memberId,
            String writerIp,
            String matchedWord,
            LocalDateTime heldAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.memberId = memberId;
        this.writerIp = writerIp;
        this.matchedWord = matchedWord;
        this.heldAt = heldAt;
    }

    /** 보류 중인 글을 다시 금칙어가 든 내용으로 고쳤을 때. 검토 목록 맨 위로 오고 비회원 확인 시간도 다시 잰다. */
    public void refresh(String matchedWord, LocalDateTime heldAt) {
        this.matchedWord = matchedWord;
        this.heldAt = heldAt;
    }
}
