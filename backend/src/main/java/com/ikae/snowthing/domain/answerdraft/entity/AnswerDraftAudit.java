package com.ikae.snowthing.domain.answerdraft.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 초안 감사 기록 (029). 저장, 승인, 거절, 게시, 게시 거절을 남긴다. 본문이나 토큰 값은 넣지 않고 행위자와 짧은 사유 코드만 둔다.
 *
 * <p>게시 거절 기록은 게시 트랜잭션이 되돌려진 뒤 별도 트랜잭션으로 쓴다. 그래서 거절되어도 기록은 남는다.
 */
@Entity
@Table(
        name = "answer_draft_audit",
        indexes = {
            @Index(
                    name = "idx_answer_draft_audit_draft_created",
                    columnList = "answer_draft_id,created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerDraftAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_draft_audit_id")
    private Long id;

    @Column(name = "answer_draft_id", nullable = false)
    private Long draftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private AnswerDraftAuditAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_member_id",
            foreignKey = @ForeignKey(name = "fk_answer_draft_audit_actor"))
    private Member actor;

    @Column(name = "actor_token_id")
    private Long actorTokenId;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AnswerDraftAudit of(
            Long draftId,
            AnswerDraftAuditAction action,
            Member actor,
            Long actorTokenId,
            String reasonCode,
            LocalDateTime createdAt) {
        AnswerDraftAudit audit = new AnswerDraftAudit();
        audit.draftId = draftId;
        audit.action = action;
        audit.actor = actor;
        audit.actorTokenId = actorTokenId;
        audit.reasonCode = reasonCode;
        audit.createdAt = createdAt;
        return audit;
    }
}
