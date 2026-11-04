package com.ikae.snowthing.domain.answerdraft.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftDetailResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftListResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftPublishResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftRejectRequest;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftSaveRequest;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftStatusResponse;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAuditAction;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;
import com.ikae.snowthing.domain.answerdraft.repository.AnswerDraftRepository;
import com.ikae.snowthing.domain.search.dto.EvidenceDetailResponse;
import com.ikae.snowthing.domain.search.service.EvidenceService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 답변 초안 흐름의 입구. 트랜잭션을 직접 열지 않고, 트랜잭션 서비스를 부른 뒤 그 바깥에서 할 일(멱등성 경합 처리, 게시 거절 기록, 근거 재확인)을 한다. */
@Service
@RequiredArgsConstructor
public class AnswerDraftService {

    static final int MAX_LIST_SIZE = 50;
    static final int MAX_REJECT_REASON_LENGTH = 255;

    private final AnswerDraftCommandService commands;
    private final AnswerDraftQueryService queries;
    private final AnswerDraftAuditWriter auditWriter;
    private final AnswerDraftRepository drafts;
    private final AnswerDraftPublishPolicy publishPolicy;
    private final EvidenceService evidenceService;

    /**
     * 초안을 저장한다. 같은 작성자가 같은 멱등성 키로 같은 내용을 다시 보내면 처음 초안을 돌려준다({@code replayed=true}).
     *
     * <p>두 요청이 동시에 같은 키로 들어오면 한쪽은 유일 제약 위반으로 실패한다. 그때 먼저 저장된 초안을 다시 읽어 돌려준다.
     */
    public SaveResult save(AnswerDraftSaveRequest request, CustomUserDetails principal) {
        AnswerDraftInput input = AnswerDraftInput.from(request);
        Long memberId = principal.getMember().getId();
        Long tokenId = principal instanceof ApiTokenUserDetails token ? token.getTokenId() : null;

        var replay = commands.findReplay(memberId, input.idempotencyKey(), input.requestHash());
        if (replay.isPresent()) {
            return new SaveResult(replay.get(), true);
        }
        try {
            return new SaveResult(commands.save(input, memberId, tokenId), false);
        } catch (DataIntegrityViolationException e) {
            return commands.findReplay(memberId, input.idempotencyKey(), input.requestHash())
                    .map(existing -> new SaveResult(existing, true))
                    .orElseThrow(() -> e);
        }
    }

    public AnswerDraftStatusResponse status(String publicId, CustomUserDetails principal) {
        Long creator =
                principal instanceof ApiTokenUserDetails ? principal.getMember().getId() : null;
        return queries.status(publicId, creator);
    }

    public AnswerDraftListResponse list(String rawStatus, Integer page, Integer size) {
        AnswerDraftStatus status = null;
        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                status = AnswerDraftStatus.valueOf(rawStatus.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_REQUEST);
            }
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_LIST_SIZE) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_REQUEST);
        }
        return queries.list(status, pageNumber, pageSize, publishPolicy.isPublishEnabled());
    }

    /** 상세. 근거마다 지금 원문을 다시 읽어 저장 시점 해시와 비교한 결과를 붙인다. 근거 조회는 각자 짧은 읽기 트랜잭션으로 한다. */
    public AnswerDraftDetailResponse detail(String publicId) {
        AnswerDraftDetailResponse base = queries.detail(publicId, publishPolicy.isPublishEnabled());
        var evidence = base.evidence().stream().map(this::withCurrentStatus).toList();
        return new AnswerDraftDetailResponse(
                base.draftId(),
                base.status(),
                base.version(),
                base.target(),
                base.body(),
                evidence,
                base.createdByNickname(),
                base.createdByToken(),
                base.createdAt(),
                base.reviewedByNickname(),
                base.reviewedAt(),
                base.approvedByNickname(),
                base.approvedAt(),
                base.approvedPostContentVersion(),
                base.rejectReason(),
                base.publishedCommentId(),
                base.publishedByNickname(),
                base.publishedAt(),
                base.publishEnabled(),
                base.audits());
    }

    public AnswerDraftStatusResponse approve(String publicId, CustomUserDetails admin) {
        return commands.approve(publicId, admin.getMember().getId());
    }

    public AnswerDraftStatusResponse reject(
            String publicId, AnswerDraftRejectRequest request, CustomUserDetails admin) {
        String reason =
                request == null || request.reason() == null ? null : request.reason().strip();
        if (reason != null && reason.isEmpty()) {
            reason = null;
        }
        if (reason != null && reason.length() > MAX_REJECT_REASON_LENGTH) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_INVALID_REQUEST);
        }
        return commands.reject(publicId, admin.getMember().getId(), reason);
    }

    /** 게시. 플래그가 꺼져 있으면 초안을 건드리지 않고 거절한다. 게시 직전 검사에서 거절되면 게시 트랜잭션을 되돌린 뒤 거절 사유를 따로 기록한다. */
    public AnswerDraftPublishResponse publish(
            String publicId, CustomUserDetails admin, String clientIp) {
        Long adminId = admin.getMember().getId();
        if (!publishPolicy.isPublishEnabled()) {
            drafts.findByPublicId(publicId)
                    .ifPresent(
                            draft ->
                                    auditWriter.recordRejection(
                                            draft.getId(),
                                            AnswerDraftAuditAction.PUBLISH_REJECTED,
                                            adminId,
                                            ErrorCode.ANSWER_DRAFT_PUBLISH_DISABLED.name()));
            throw new CustomException(ErrorCode.ANSWER_DRAFT_PUBLISH_DISABLED);
        }
        try {
            return commands.publish(publicId, adminId, clientIp);
        } catch (PublishRejectedException e) {
            auditWriter.recordRejection(
                    e.getDraftId(),
                    AnswerDraftAuditAction.PUBLISH_REJECTED,
                    adminId,
                    e.getErrorCode().name());
            throw new CustomException(e.getErrorCode());
        }
    }

    private AnswerDraftDetailResponse.Evidence withCurrentStatus(
            AnswerDraftDetailResponse.Evidence saved) {
        try {
            EvidenceDetailResponse current = evidenceService.getEvidence(saved.evidenceId(), null);
            String status = current.chunkHash().equals(saved.chunkHash()) ? "MATCH" : "CHANGED";
            return new AnswerDraftDetailResponse.Evidence(
                    saved.evidenceId(),
                    saved.chunkHash(),
                    status,
                    current.title(),
                    current.url(),
                    current.text());
        } catch (CustomException e) {
            return new AnswerDraftDetailResponse.Evidence(
                    saved.evidenceId(), saved.chunkHash(), "UNAVAILABLE", null, null, null);
        }
    }

    /**
     * @param replayed 같은 멱등성 키의 기존 초안을 돌려줬으면 true
     */
    public record SaveResult(AnswerDraftStatusResponse draft, boolean replayed) {}
}
