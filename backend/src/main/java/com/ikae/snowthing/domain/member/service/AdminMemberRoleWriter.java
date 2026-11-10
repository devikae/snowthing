package com.ikae.snowthing.domain.member.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.aimember.persona.PersonaFileLoader;
import com.ikae.snowthing.domain.member.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 권한 지정과 해제 트랜잭션 (IKA-77). 세션 무효화는 커밋 뒤에 호출자가 한다.
 *
 * <p>지정과 해제 모두 먼저 활성 관리자 행을 ID 순서로 잠근다. 두 관리자가 서로를 동시에 해제해도 뒤 트랜잭션은 앞 트랜잭션의 커밋을 기다렸다가 자신이 이미 관리자가
 * 아님을 보고 거부된다. 그래서 관리자가 0명이 되지 않는다. 관리자 수는 몇 명뿐이라 잠금 범위가 작다.
 *
 * <p>세션에 든 권한은 로그인 때 스냅샷이라 호출한 관리자의 현재 권한을 잠근 행에서 다시 확인한다.
 */
@Component
@RequiredArgsConstructor
public class AdminMemberRoleWriter {

    private final MemberRepository memberRepository;

    public record Change(Role previous, AdminMemberResponse member) {
        boolean changed() {
            return previous != member.role();
        }
    }

    @Transactional
    public Change grant(String actorPublicId, String targetPublicId) {
        List<Member> admins = lockActiveAdmins();
        requireActor(admins, actorPublicId);
        Member target = requireForUpdate(targetPublicId);
        Role previous = target.getRole();
        if (previous == Role.ROLE_ADMIN) {
            return new Change(previous, AdminMemberResponse.from(target));
        }
        if (target.getStatus() != MemberStatus.ACTIVE || isAiMember(target)) {
            throw new CustomException(ErrorCode.ADMIN_ROLE_INELIGIBLE_MEMBER);
        }
        target.changeRole(Role.ROLE_ADMIN);
        return new Change(previous, AdminMemberResponse.from(target));
    }

    @Transactional
    public Change revoke(String actorPublicId, String targetPublicId) {
        List<Member> admins = lockActiveAdmins();
        requireActor(admins, actorPublicId);
        Member target = requireForUpdate(targetPublicId);
        Role previous = target.getRole();
        if (previous != Role.ROLE_ADMIN) {
            return new Change(previous, AdminMemberResponse.from(target));
        }
        boolean otherAdminRemains =
                admins.stream().anyMatch(admin -> !admin.getId().equals(target.getId()));
        if (!otherAdminRemains) {
            throw new CustomException(ErrorCode.ADMIN_ROLE_LAST_ADMIN);
        }
        if (target.getPublicId().equals(actorPublicId)) {
            throw new CustomException(ErrorCode.ADMIN_ROLE_SELF_REVOKE_NOT_ALLOWED);
        }
        target.changeRole(Role.ROLE_USER);
        return new Change(previous, AdminMemberResponse.from(target));
    }

    private List<Member> lockActiveAdmins() {
        List<Long> ids =
                memberRepository.findIdsByRoleAndStatus(Role.ROLE_ADMIN, MemberStatus.ACTIVE);
        if (ids.isEmpty()) {
            return List.of();
        }
        // 잠그는 사이 바뀐 행을 거른다. 잠근 읽기는 커밋된 최신 값을 본다.
        return memberRepository.findAllByIdInForUpdate(ids).stream()
                .filter(
                        member ->
                                member.getRole() == Role.ROLE_ADMIN
                                        && member.getStatus() == MemberStatus.ACTIVE)
                .toList();
    }

    private static void requireActor(List<Member> admins, String actorPublicId) {
        boolean actorIsAdmin =
                admins.stream().anyMatch(admin -> admin.getPublicId().equals(actorPublicId));
        if (!actorIsAdmin) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Member requireForUpdate(String publicId) {
        return memberRepository
                .findByPublicIdForUpdate(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private static boolean isAiMember(Member member) {
        return member.getEmail() != null
                && member.getEmail().endsWith(PersonaFileLoader.EMAIL_DOMAIN);
    }
}
