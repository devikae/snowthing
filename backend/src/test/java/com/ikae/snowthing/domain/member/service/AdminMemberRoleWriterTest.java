package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/** 관리자 권한 규칙 단위 테스트 (IKA-77). 공유 DB에서는 관리자가 한 명뿐인 상황을 만들기 어려워 마지막 관리자 규칙은 여기서 본다. */
@ExtendWith(MockitoExtension.class)
class AdminMemberRoleWriterTest {

    @Mock private MemberRepository memberRepository;
    @Mock private AdminActionLogger actionLogger;
    @InjectMocks private AdminMemberRoleWriter writer;

    @Test
    @DisplayName("활성 일반 회원을 관리자로 지정한다")
    void grantsActiveMember() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        Member target = member(2L, "target", Role.ROLE_USER, MemberStatus.ACTIVE, "t@x.com");
        givenAdmins(admin);
        givenTarget(target);

        AdminMemberRoleWriter.Change change = writer.grant("admin", "target");

        assertThat(change.changed()).isTrue();
        assertThat(target.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("정지, 탈퇴, AI 회원은 관리자로 지정할 수 없다")
    void rejectsIneligibleTargets() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        List<Member> targets =
                List.of(
                        member(2L, "s", Role.ROLE_USER, MemberStatus.SUSPENDED, "s@x.com"),
                        member(3L, "w", Role.ROLE_USER, MemberStatus.WITHDRAWN, "w@x.com"),
                        member(
                                4L,
                                "ai",
                                Role.ROLE_USER,
                                MemberStatus.ACTIVE,
                                "p@snowthing.invalid"));
        givenAdmins(admin);
        for (Member target : targets) {
            givenTarget(target);
            assertError(
                    () -> writer.grant("admin", target.getPublicId()),
                    ErrorCode.ADMIN_ROLE_INELIGIBLE_MEMBER);
            assertThat(target.getRole()).isEqualTo(Role.ROLE_USER);
        }
    }

    @Test
    @DisplayName("다른 관리자를 해제한다")
    void revokesOtherAdmin() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        Member other = member(2L, "other", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "o@x.com");
        givenAdmins(admin, other);
        givenTarget(other);

        AdminMemberRoleWriter.Change change = writer.revoke("admin", "other");

        assertThat(change.changed()).isTrue();
        assertThat(other.getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("다른 관리자가 있어도 자신은 해제할 수 없다")
    void rejectsSelfRevoke() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        Member other = member(2L, "other", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "o@x.com");
        givenAdmins(admin, other);
        givenTarget(admin);

        assertError(
                () -> writer.revoke("admin", "admin"),
                ErrorCode.ADMIN_ROLE_SELF_REVOKE_NOT_ALLOWED);
        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("마지막 남은 관리자는 해제할 수 없다")
    void rejectsLastAdmin() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        givenAdmins(admin);
        givenTarget(admin);

        assertError(() -> writer.revoke("admin", "admin"), ErrorCode.ADMIN_ROLE_LAST_ADMIN);
        assertThat(admin.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("잠근 행에서 호출자가 이미 관리자가 아니면 거부한다 (동시 해제의 뒤 요청)")
    void rejectsActorWhoLostAdminRole() {
        Member former = member(1L, "former", Role.ROLE_USER, MemberStatus.ACTIVE, "f@x.com");
        Member other = member(2L, "other", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "o@x.com");
        when(memberRepository.findIdsByRoleAndStatus(Role.ROLE_ADMIN, MemberStatus.ACTIVE))
                .thenReturn(List.of(1L, 2L));
        when(memberRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of(former, other));

        assertError(() -> writer.revoke("former", "other"), ErrorCode.ACCESS_DENIED);
        assertError(() -> writer.grant("former", "other"), ErrorCode.ACCESS_DENIED);
        assertThat(other.getRole()).isEqualTo(Role.ROLE_ADMIN);
        verify(memberRepository, never()).findByPublicIdForUpdate("other");
    }

    @Test
    @DisplayName("이미 그 권한이면 바꾸지 않고 성공한다")
    void unchangedWhenAlreadyInRole() {
        Member admin = member(1L, "admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE, "a@x.com");
        Member user = member(2L, "user", Role.ROLE_USER, MemberStatus.ACTIVE, "u@x.com");
        givenAdmins(admin);
        givenTarget(user);
        assertThat(writer.revoke("admin", "user").changed()).isFalse();
        givenTarget(admin);
        assertThat(writer.grant("admin", "admin").changed()).isFalse();
    }

    private void givenAdmins(Member... admins) {
        when(memberRepository.findIdsByRoleAndStatus(Role.ROLE_ADMIN, MemberStatus.ACTIVE))
                .thenReturn(Arrays.stream(admins).map(Member::getId).toList());
        when(memberRepository.findAllByIdInForUpdate(anyList())).thenReturn(List.of(admins));
    }

    private void givenTarget(Member target) {
        when(memberRepository.findByPublicIdForUpdate(target.getPublicId()))
                .thenReturn(Optional.of(target));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CustomException.class)
                .extracting(thrown -> ((CustomException) thrown).getErrorCode())
                .isEqualTo(expected);
    }

    private static Member member(
            Long id, String publicId, Role role, MemberStatus status, String email) {
        Member member =
                Member.builder()
                        .publicId(publicId)
                        .email(email)
                        .nickname(publicId)
                        .role(role)
                        .status(status)
                        .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
