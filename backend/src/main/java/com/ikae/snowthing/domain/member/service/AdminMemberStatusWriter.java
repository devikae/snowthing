package com.ikae.snowthing.domain.member.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.member.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 상태 변경 트랜잭션. 행 잠금으로 동시 변경을 줄 세운다. 세션 무효화는 커밋 뒤에 호출자가 한다. */
@Component
@RequiredArgsConstructor
public class AdminMemberStatusWriter {

    private final MemberRepository memberRepository;
    private final AdminActionLogger actionLogger;

    public record Change(Long memberId, MemberStatus previous, AdminMemberResponse member) {
        boolean changed() {
            return previous != member.status();
        }
    }

    @Transactional
    public Change suspend(String actorPublicId, String targetPublicId) {
        Member target = requireForUpdate(targetPublicId);
        if (target.getPublicId().equals(actorPublicId)) {
            throw new CustomException(ErrorCode.MEMBER_SELF_SUSPEND_NOT_ALLOWED);
        }
        if (target.getRole() == Role.ROLE_ADMIN) {
            throw new CustomException(ErrorCode.MEMBER_ADMIN_SUSPEND_NOT_ALLOWED);
        }
        rejectWithdrawn(target);
        MemberStatus previous = target.getStatus();
        target.suspend();
        if (previous != MemberStatus.SUSPENDED) {
            actionLogger.record(
                    AdminActionType.MEMBER_SUSPEND, AdminTargetType.MEMBER, targetPublicId, null);
        }
        return new Change(target.getId(), previous, AdminMemberResponse.from(target));
    }

    @Transactional
    public Change reinstate(String targetPublicId) {
        Member target = requireForUpdate(targetPublicId);
        rejectWithdrawn(target);
        MemberStatus previous = target.getStatus();
        target.reinstate();
        if (previous != MemberStatus.ACTIVE) {
            actionLogger.record(
                    AdminActionType.MEMBER_REINSTATE, AdminTargetType.MEMBER, targetPublicId, null);
        }
        return new Change(target.getId(), previous, AdminMemberResponse.from(target));
    }

    private Member requireForUpdate(String publicId) {
        return memberRepository
                .findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private static void rejectWithdrawn(Member member) {
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.MEMBER_WITHDRAWN_STATUS_LOCKED);
        }
    }
}
