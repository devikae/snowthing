package com.ikae.snowthing.domain.answerdraft.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI가 저장하고 사람이 검토해 게시하는 답변 초안 (029).
 *
 * <p>대상 게시글은 ID만 들고 연관으로 잡지 않는다. Post에는 {@code @SQLRestriction("is_deleted = false")}가 있어 삭제된 게시글을
 * 연관으로 읽으면 엔티티를 찾지 못한다. 게시 직전 검사는 어차피 원문을 JDBC로 다시 읽는다.
 *
 * <p>{@code published_comment_id}는 유일 제약이 있어 한 댓글이 두 초안의 게시 결과가 될 수 없다. 같은 초안을 두 번 게시하지 않는 것은 게시
 * 트랜잭션이 초안 행을 비관적 잠금으로 잡고 상태를 다시 확인해서 보장한다.
 */
@Entity
@Table(
        name = "answer_draft",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_answer_draft_public_id", columnNames = "public_id"),
            @UniqueConstraint(
                    name = "uk_answer_draft_creator_idempotency",
                    columnNames = {"created_by_member_id", "idempotency_key"}),
            @UniqueConstraint(
                    name = "uk_answer_draft_published_comment",
                    columnNames = "published_comment_id")
        },
        indexes = {
            @Index(
                    name = "idx_answer_draft_status_created",
                    columnList = "status,created_at,answer_draft_id"),
            @Index(name = "idx_answer_draft_target_post", columnList = "target_post_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnswerDraft extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_draft_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @Column(name = "target_post_id", nullable = false)
    private Long targetPostId;

    @Column(name = "target_post_public_id", nullable = false, length = 36)
    private String targetPostPublicId;

    @Column(name = "body", nullable = false, length = 1000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnswerDraftStatus status;

    // 키 형식은 대소문자를 구분하므로 비교도 이진 정렬로 한다(029와 같다).
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100,
            columnDefinition = "varchar(100) character set utf8mb4 collate utf8mb4_bin")
    private String idempotencyKey;

    /** 멱등성 키 재사용 판별용. 대상, 본문, 근거 목록을 정해진 순서로 이어 만든 SHA-256. */
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_answer_draft_created_by"))
    private Member createdBy;

    /** 토큰으로 저장했으면 api_token ID, 관리자 세션으로 저장했으면 null. */
    @Column(name = "created_by_token_id")
    private Long createdByTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reviewed_by_member_id",
            foreignKey = @ForeignKey(name = "fk_answer_draft_reviewed_by"))
    private Member reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "approved_by_member_id",
            foreignKey = @ForeignKey(name = "fk_answer_draft_approved_by"))
    private Member approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** 승인 시점의 대상 게시글 post.content_version. 게시 직전 값과 다르면 게시를 거절한다. */
    @Column(name = "approved_post_content_version")
    private Long approvedPostContentVersion;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "published_comment_id")
    private Long publishedCommentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "published_by_member_id",
            foreignKey = @ForeignKey(name = "fk_answer_draft_published_by"))
    private Member publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 목록에서 근거 수를 셀 때 초안마다 따로 읽지 않고 한 번에 묶어 읽는다.
    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @BatchSize(size = 50)
    private List<AnswerDraftEvidence> evidences = new ArrayList<>();

    public static AnswerDraft create(
            long targetPostId,
            String targetPostPublicId,
            String body,
            String idempotencyKey,
            String requestHash,
            Member createdBy,
            Long createdByTokenId) {
        AnswerDraft draft = new AnswerDraft();
        draft.publicId = UUID.randomUUID().toString();
        draft.targetPostId = targetPostId;
        draft.targetPostPublicId = targetPostPublicId;
        draft.body = body;
        draft.idempotencyKey = idempotencyKey;
        draft.requestHash = requestHash;
        draft.createdBy = createdBy;
        draft.createdByTokenId = createdByTokenId;
        draft.status = AnswerDraftStatus.DRAFT;
        return draft;
    }

    public void addEvidence(String evidenceId, String chunkHash) {
        evidences.add(new AnswerDraftEvidence(this, evidenceId, chunkHash, evidences.size()));
    }

    /** 저장 검증(대상 공개 여부, 근거 해시)을 통과한 뒤 부른다. */
    public void submitForReview() {
        requireStatus(AnswerDraftStatus.DRAFT);
        if (evidences.isEmpty()) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_REQUEST);
        }
        status = AnswerDraftStatus.REVIEW_REQUIRED;
    }

    public void approve(Member approver, LocalDateTime now, long targetPostContentVersion) {
        requireStatus(AnswerDraftStatus.REVIEW_REQUIRED);
        status = AnswerDraftStatus.APPROVED;
        reviewedBy = approver;
        reviewedAt = now;
        approvedBy = approver;
        approvedAt = now;
        approvedPostContentVersion = targetPostContentVersion;
    }

    public void reject(Member reviewer, LocalDateTime now, String reason) {
        if (status != AnswerDraftStatus.REVIEW_REQUIRED && status != AnswerDraftStatus.APPROVED) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_STATUS);
        }
        status = AnswerDraftStatus.REJECTED;
        reviewedBy = reviewer;
        reviewedAt = now;
        rejectReason = reason;
    }

    public void markPublished(long commentId, Member publisher, LocalDateTime now) {
        requireStatus(AnswerDraftStatus.APPROVED);
        status = AnswerDraftStatus.PUBLISHED;
        publishedCommentId = commentId;
        publishedBy = publisher;
        publishedAt = now;
    }

    private void requireStatus(AnswerDraftStatus expected) {
        if (status != expected) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_STATUS);
        }
    }
}
