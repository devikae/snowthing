package com.ikae.snowthing.domain.answerdraft.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftDetailResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftDetailResponse.Audit;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftDetailResponse.Target;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftListResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftStatusResponse;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraft;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftEvidence;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;
import com.ikae.snowthing.domain.answerdraft.repository.AnswerDraftAuditRepository;
import com.ikae.snowthing.domain.answerdraft.repository.AnswerDraftRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.PostSource;
import com.ikae.snowthing.domain.search.service.EvidenceUrls;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AnswerDraftQueryService {

    private static final int BODY_PREVIEW_LENGTH = 120;

    private final AnswerDraftRepository drafts;
    private final AnswerDraftAuditRepository audits;
    private final SearchSourceRepository sources;

    /**
     * 상태 조회. {@code creatorMemberId}가 있으면(토큰 요청) 그 회원이 만든 초안만 보인다. 다른 회원의 초안은 존재 여부도 알리지 않고 404로
     * 답한다.
     */
    AnswerDraftStatusResponse status(String publicId, Long creatorMemberId) {
        AnswerDraft draft = find(publicId);
        if (creatorMemberId != null && !creatorMemberId.equals(draft.getCreatedBy().getId())) {
            throw new CustomException(ErrorCode.ANSWER_DRAFT_NOT_FOUND);
        }
        return AnswerDraftStatusResponse.from(
                draft, visibleTarget(draft).map(this::url).orElse(null));
    }

    AnswerDraftListResponse list(
            AnswerDraftStatus status, int page, int size, boolean publishEnabled) {
        PageRequest pageable =
                PageRequest.of(
                        page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AnswerDraft> result =
                status == null ? drafts.findAll(pageable) : drafts.findByStatus(status, pageable);
        List<AnswerDraftListResponse.Item> items =
                result.getContent().stream()
                        .map(
                                draft ->
                                        new AnswerDraftListResponse.Item(
                                                draft.getPublicId(),
                                                draft.getStatus(),
                                                draft.getTargetPostPublicId(),
                                                preview(draft.getBody()),
                                                draft.getEvidences().size(),
                                                draft.getCreatedBy().getNickname(),
                                                draft.getCreatedByTokenId() != null,
                                                draft.getCreatedAt(),
                                                draft.getUpdatedAt()))
                        .toList();
        return new AnswerDraftListResponse(items, page, size, result.hasNext(), publishEnabled);
    }

    /** 근거의 현재 상태는 비워 둔다. 근거마다 원문을 다시 읽는 일은 바깥(AnswerDraftService)에서 따로 한다. */
    AnswerDraftDetailResponse detail(String publicId, boolean publishEnabled) {
        AnswerDraft draft = find(publicId);
        Optional<PostSource> target = visibleTarget(draft);
        List<AnswerDraftDetailResponse.Evidence> evidence =
                draft.getEvidences().stream()
                        .map(
                                (AnswerDraftEvidence item) ->
                                        new AnswerDraftDetailResponse.Evidence(
                                                item.getEvidenceId(),
                                                item.getChunkHash(),
                                                null,
                                                null,
                                                null,
                                                null))
                        .toList();
        List<Audit> auditItems =
                audits.findByDraftIdOrderByCreatedAtAscIdAsc(draft.getId()).stream()
                        .map(
                                audit ->
                                        new Audit(
                                                audit.getAction(),
                                                nickname(audit.getActor()),
                                                audit.getActorTokenId() != null,
                                                audit.getReasonCode(),
                                                audit.getCreatedAt()))
                        .toList();
        return new AnswerDraftDetailResponse(
                draft.getPublicId(),
                draft.getStatus(),
                draft.getVersion(),
                new Target(
                        draft.getTargetPostPublicId(),
                        target.isPresent(),
                        target.map(PostSource::title).orElse(null),
                        target.map(this::url).orElse(null),
                        target.map(PostSource::contentVersion).orElse(null)),
                draft.getBody(),
                evidence,
                nickname(draft.getCreatedBy()),
                draft.getCreatedByTokenId() != null,
                draft.getCreatedAt(),
                nickname(draft.getReviewedBy()),
                draft.getReviewedAt(),
                nickname(draft.getApprovedBy()),
                draft.getApprovedAt(),
                draft.getApprovedPostContentVersion(),
                draft.getRejectReason(),
                draft.getPublishedCommentId(),
                nickname(draft.getPublishedBy()),
                draft.getPublishedAt(),
                publishEnabled,
                auditItems);
    }

    private AnswerDraft find(String publicId) {
        return drafts.findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANSWER_DRAFT_NOT_FOUND));
    }

    private Optional<PostSource> visibleTarget(AnswerDraft draft) {
        return sources.findVisiblePost(draft.getTargetPostId());
    }

    private String url(PostSource post) {
        return EvidenceUrls.postUrl(post.categoryCode(), post.publicId());
    }

    private static String nickname(Member member) {
        return member == null ? null : member.getNickname();
    }

    private static String preview(String body) {
        if (body.length() <= BODY_PREVIEW_LENGTH) {
            return body;
        }
        int end = BODY_PREVIEW_LENGTH;
        if (Character.isHighSurrogate(body.charAt(end - 1))) {
            end--;
        }
        return body.substring(0, end);
    }
}
