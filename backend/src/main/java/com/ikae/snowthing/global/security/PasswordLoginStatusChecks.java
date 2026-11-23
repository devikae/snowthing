package com.ikae.snowthing.global.security;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.global.exception.AccountSuspendedException;

/**
 * 이메일 로그인의 계정 상태 검사 (IKA-115).
 *
 * <ul>
 *   <li>탈퇴 계정은 비밀번호 검사 전에 막고, 없는 이메일과 같은 401을 받는다. 지금까지와 같다.
 *   <li>정지 계정은 비밀번호가 맞은 뒤에 막고 풀리는 시각을 알려 준다. 비밀번호를 아는 본인에게만 상태가 보인다. 예전에는 비밀번호 검사 전에 막아 정지된 본인도
 *       "비밀번호가 틀렸다"는 안내를 받았다.
 * </ul>
 */
public final class PasswordLoginStatusChecks {

    private PasswordLoginStatusChecks() {}

    /** 정지가 아닌 비활성 계정(탈퇴)을 막는다. 정지는 {@link #afterPassword}가 본다. */
    public static void beforePassword(UserDetails user) {
        if (!user.isEnabled() && !isSuspended(user)) {
            throw new DisabledException("disabled");
        }
    }

    public static void afterPassword(UserDetails user) {
        if (isSuspended(user)) {
            Member member = ((CustomUserDetails) user).getMember();
            throw new AccountSuspendedException(member.getSuspendedUntil());
        }
    }

    private static boolean isSuspended(UserDetails user) {
        return user instanceof CustomUserDetails details
                && details.getMember().getStatus() == MemberStatus.SUSPENDED;
    }
}
