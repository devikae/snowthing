package com.ikae.snowthing.domain.answerdraft.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftPublishResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftSaveRequest.EvidenceRef;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftStatusResponse;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraft;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAuditAction;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftEvidence;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;
import com.ikae.snowthing.domain.answerdraft.repository.AnswerDraftRepository;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.PostSource;
import com.ikae.snowthing.domain.search.service.EvidenceService;
import com.ikae.snowthing.domain.search.service.EvidenceUrls;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 초안 상태를 바꾸는 트랜잭션. 검증 실패로 예외가 나면 트랜잭션 전체가 되돌려진다.
 *
 * <p>근거 검사는 {@link EvidenceService#getEvidence}를 그대로 쓴다. 공개 범위를 먼저 보고 해시를 비교하므로 볼 수 없는 원문의 해시 일치
 * 여부는 알려 주지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
class AnswerDraftCommandService {

    private final AnswerDraftRepository drafts;
    private final MemberRepository members;
    private final PostRepository posts;
    private final SearchSourceRepository sources;
    private final EvidenceService evidenceService;
    private final CommentService commentService;
    private final AnswerDraftAuditWriter auditWriter;

    /** 같은 작성자, 같은 멱등성 키의 초안이 있으면 그대로 돌려준다. 내용이 다르면 409. */
    @Transactional(readOnly = true)
    Optional<AnswerDraftStatusResponse> findReplay(
            Long memberId, String idempotencyKey, String requestHash) {
        return drafts.findByCreatedByIdAndIdempotencyKey(memberId, idempotencyKey)
                .map(
                        existing -> {
                            if (!existing.getRequestHash().equals(requestHash)) {
                                throw new CustomException(
                                        ErrorCode.ANSWER_DRAFT_IDEMPOTENCY_CONFLICT);
                            }
                            return toStatus(existing);
                        });
    }

    AnswerDraftStatusResponse save(AnswerDraftInput input, Long memberId, Long tokenId) {
        PostSource target =
                sources.findVisiblePostByPublicId(input.targetPostPublicId())
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.ANSWER_DRAFT_TARGET_NOT_FOUND));
        for (EvidenceRef evidence : input.evidence()) {
            evidenceService.getEvidence(evidence.evidenceId(), evidence.chunkHash());
        }

        AnswerDraft draft =
                AnswerDraft.create(
                        target.postId(),
                        target.publicId(),
                        input.body(),
                        input.idempotencyKey(),
                        input.requestHash(),
                        members.getReferenceById(memberId),
                        tokenId);
        input.evidence().forEach(ref -> draft.addEvidence(ref.evidenceId(), ref.chunkHash()));
        draft.submitForReview();
        drafts.saveAndFlush(draft);
        auditWriter.record(draft.getId(), AnswerDraftAuditAction.SAVE, memberId, tokenId, null);
        return AnswerDraftStatusResponse.from(
                draft, EvidenceUrls.postUrl(target.categoryCode(), target.publicId()));
    }

    /**
     * 사람이 승인한다. 승인 시점에도 대상 게시글과 근거를 다시 확인하고, 그때의 대상 게시글 content_version을 남긴다. 게시 직전에 이 값과 비교한다.
     */
    AnswerDraftStatusResponse approve(String publicId, Long adminMemberId) {
        AnswerDraft draft = lockDraft(publicId);
        if (draft.getStatus() != AnswerDraftStatus.REVIEW_REQUIRED) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_STATUS);
        }
        Member admin = activeAdmin(adminMemberId);
        PostSource target =
                sources.findVisiblePost(draft.getTargetPostId())
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.ANSWER_DRAFT_TARGET_UNAVAILABLE));
        if (!evidenceStillMatches(draft)) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_EVIDENCE_CHANGED);
        }
        draft.approve(admin, LocalDateTime.now(), target.contentVersion());
        drafts.flush();
        auditWriter.record(
                draft.getId(), AnswerDraftAuditAction.APPROVE, adminMemberId, null, null);
        return AnswerDraftStatusResponse.from(
                draft, EvidenceUrls.postUrl(target.categoryCode(), target.publicId()));
    }

    AnswerDraftStatusResponse reject(String publicId, Long adminMemberId, String reason) {
        AnswerDraft draft = lockDraft(publicId);
        draft.reject(activeAdmin(adminMemberId), LocalDateTime.now(), reason);
        drafts.flush();
        auditWriter.record(draft.getId(), AnswerDraftAuditAction.REJECT, adminMemberId, null, null);
        return toStatus(draft);
    }

    /**
     * 승인된 초안을 댓글로 게시한다.
     *
     * <ol>
     *   <li>초안 행을 비관적 잠금으로 잡는다. 동시에 온 두 번째 요청은 여기서 기다렸다가 PUBLISHED를 보고 첫 댓글을 그대로 돌려받는다.
     *   <li>대상 게시글 행도 잠근다. 게시글 수정 경로가 같은 행 잠금을 쓰므로 검사와 댓글 작성 사이에 내용이 바뀌지 않는다.
     *   <li>대상 공개 범위, 승인 이후 content_version, 승인자 권한, 근거 해시를 다시 확인한다.
     *   <li>기존 댓글 작성 경로(CommentService)로 승인자 이름의 댓글을 만든다. 검색 outbox와 댓글 수 갱신이 그대로 일어난다.
     * </ol>
     *
     * 근거 원문 행은 잠그지 않는다. 근거 확인과 커밋 사이에 근거 원문이 바뀌는 짧은 구간은 남는다.
     */
    AnswerDraftPublishResponse publish(String publicId, Long publisherMemberId, String clientIp) {
        AnswerDraft draft = lockDraft(publicId);
        if (draft.getStatus() == AnswerDraftStatus.PUBLISHED) {
            return toPublishResponse(draft, true);
        }
        Long draftId = draft.getId();
        if (draft.getStatus() != AnswerDraftStatus.APPROVED) {
            throw new PublishRejectedException(draftId, ErrorCode.ANSWER_DRAFT_INVALID_STATUS);
        }
        Member publisher = activeAdmin(publisherMemberId);

        Post lockedPost =
                posts.findByIdForUpdate(draft.getTargetPostId())
                        .orElseThrow(
                                () ->
                                        new PublishRejectedException(
                                                draftId,
                                                ErrorCode.ANSWER_DRAFT_TARGET_UNAVAILABLE));
        PostSource target =
                sources.findVisiblePost(lockedPost.getId())
                        .orElseThrow(
                                () ->
                                        new PublishRejectedException(
                                                draftId,
                                                ErrorCode.ANSWER_DRAFT_TARGET_UNAVAILABLE));
        if (draft.getApprovedPostContentVersion() == null
                || target.contentVersion() != draft.getApprovedPostContentVersion()) {
            throw new PublishRejectedException(draftId, ErrorCode.ANSWER_DRAFT_TARGET_CHANGED);
        }
        Member approver = members.findById(draft.getApprovedBy().getId()).orElse(null);
        if (!ApiTokenService.isActiveAdmin(approver)) {
            throw new PublishRejectedException(draftId, ErrorCode.ANSWER_DRAFT_APPROVER_INVALID);
        }
        if (!evidenceStillMatches(draft)) {
            throw new PublishRejectedException(draftId, ErrorCode.ANSWER_DRAFT_EVIDENCE_CHANGED);
        }

        CommentResponse comment =
                commentService.createComment(
                        target.publicId(),
                        new CommentCreateRequest(null, draft.getBody(), false, null),
                        new CustomUserDetails(approver),
                        clientIp);

        // 댓글 수 갱신 쿼리가 영속성 컨텍스트를 비우므로 잠근 초안을 다시 읽는다. 행 잠금은 트랜잭션이 끝날 때까지 유지된다.
        AnswerDraft published =
                drafts.findById(draftId)
                        .orElseThrow(() -> new CustomException(ErrorCode.ANSWER_DRAFT_NOT_FOUND));
        published.markPublished(comment.commentId(), publisher, LocalDateTime.now());
        drafts.flush();
        auditWriter.record(
                draftId, AnswerDraftAuditAction.PUBLISH, publisherMemberId, null, null);
        return toPublishResponse(published, false);
    }

    private boolean evidenceStillMatches(AnswerDraft draft) {
        for (AnswerDraftEvidence evidence : draft.getEvidences()) {
            try {
                evidenceService.getEvidence(evidence.getEvidenceId(), evidence.getChunkHash());
            } catch (CustomException e) {
                return false;
            }
        }
        return true;
    }

    private AnswerDraft lockDraft(String publicId) {
        return drafts.findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANSWER_DRAFT_NOT_FOUND));
    }

    /** 세션의 회원 정보는 로그인 시점 값이다. 승인과 게시는 지금 DB 기준으로 활성 관리자인지 다시 본다. */
    private Member activeAdmin(Long memberId) {
        Member member = members.findById(memberId).orElse(null);
        if (!ApiTokenService.isActiveAdmin(member)) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
        return member;
    }

    private AnswerDraftStatusResponse toStatus(AnswerDraft draft) {
        return AnswerDraftStatusResponse.from(
                draft,
                sources.findVisiblePost(draft.getTargetPostId())
                        .map(post -> EvidenceUrls.postUrl(post.categoryCode(), post.publicId()))
                        .orElse(null));
    }

    private static AnswerDraftPublishResponse toPublishResponse(
            AnswerDraft draft, boolean alreadyPublished) {
        return new AnswerDraftPublishResponse(
                draft.getPublicId(),
                draft.getStatus(),
                draft.getPublishedCommentId(),
                draft.getPublishedAt(),
                draft.getTargetPostPublicId(),
                alreadyPublished);
    }
}
