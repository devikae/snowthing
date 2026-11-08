package com.ikae.snowthing.domain.answerdraft.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAudit;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAuditAction;
import com.ikae.snowthing.domain.answerdraft.repository.AnswerDraftAuditRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class AnswerDraftAuditWriter {

    private final AnswerDraftAuditRepository audits;
    private final MemberRepository members;

    /** 상태 전이와 같은 트랜잭션에서 쓴다. 전이가 되돌려지면 기록도 함께 사라진다. */
    @Transactional(propagation = Propagation.MANDATORY)
    void record(
            Long draftId,
            AnswerDraftAuditAction action,
            Long actorMemberId,
            Long actorTokenId,
            String reasonCode) {
        audits.save(
                AnswerDraftAudit.of(
                        draftId,
                        action,
                        actorMemberId == null ? null : members.getReferenceById(actorMemberId),
                        actorTokenId,
                        reasonCode,
                        LocalDateTime.now()));
    }

    /** 거절 기록. 거절된 작업의 트랜잭션이 이미 되돌려진 뒤 따로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordRejection(
            Long draftId, AnswerDraftAuditAction action, Long actorMemberId, String reasonCode) {
        record(draftId, action, actorMemberId, null, reasonCode);
    }
}
