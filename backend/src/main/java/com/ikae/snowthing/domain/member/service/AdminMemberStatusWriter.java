package com.ikae.snowthing.domain.member.service;

import java.time.LocalDateTime;
import java.util.Objects;

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
        return suspend(actorPublicId, targetPublicId, null, null, null);
    }

    /**
     * 정지한다 (IKA-115). until이 null이면 무기한이다. 이미 정지 중인 회원도 기간과 사유를 새로 쓰고, 상태나 기간, 사유가 바뀌었을 때만 조치 기록을
     * 남긴다.
     *
     * @param logLabel 조치 기록에 남길 기간 설명 (예: "7일"). null이면 기간을 적지 않는다.
     */
    @Transactional
    public Change suspend(
            String actorPublicId,
            String targetPublicId,
            LocalDateTime until,
            String reason,
            String logLabel) {
        Member target = requireForUpdate(targetPublicId);
        if (target.getPublicId().equals(actorPublicId)) {
            throw new CustomException(ErrorCode.MEMBER_SELF_SUSPEND_NOT_ALLOWED);
        }
        if (target.getRole() == Role.ROLE_ADMIN) {
            throw new CustomException(ErrorCode.MEMBER_ADMIN_SUSPEND_NOT_ALLOWED);
        }
        rejectWithdrawn(target);
        MemberStatus previous = target.getStatus();
        boolean sameTerms =
                previous == MemberStatus.SUSPENDED
                        && Objects.equals(target.getSuspendedUntil(), until)
                        && Objects.equals(target.getSuspendReason(), reason);
        target.suspend(until, reason);
        if (!sameTerms) {
            actionLogger.record(
                    AdminActionType.MEMBER_SUSPEND,
                    AdminTargetType.MEMBER,
                    targetPublicId,
                    suspendLogReason(logLabel, reason));
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

    /**
     * 기간이 끝난 정지를 푼다 (IKA-115). 잠금을 잡은 뒤 다시 확인하므로 그 사이 관리자가 기간을 늘렸으면 풀지 않는다.
     *
     * @return 실제로 풀었으면 바뀐 내용, 아니면 null
     */
    @Transactional
    public Change releaseExpired(String targetPublicId, LocalDateTime now) {
        Member target = memberRepository.findByPublicIdForUpdate(targetPublicId).orElse(null);
        if (target == null || !target.isSuspensionExpired(now)) {
            return null;
        }
        MemberStatus previous = target.getStatus();
        target.reinstate();
        actionLogger.record(
                AdminActionType.MEMBER_REINSTATE,
                AdminTargetType.MEMBER,
                targetPublicId,
                "정지 기간 만료");
        return new Change(target.getId(), previous, AdminMemberResponse.from(target));
    }

    private static String suspendLogReason(String logLabel, String reason) {
        String note = reason != null && !reason.isBlank() ? reason.strip() : null;
        if (logLabel == null) return note;
        return note == null ? logLabel : logLabel + ": " + note;
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
