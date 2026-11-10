package com.ikae.snowthing.domain.admin.service;

import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 관리자 서비스의 권한 확인. 경로({@code /api/v1/admin/**})에서 이미 ROLE_ADMIN을 요구하지만, 서비스를 다른 곳에서 부를 때도 같은 규칙이
 * 지켜지게 시장 글, 설질 제보 관리자 서비스처럼 서비스에서도 확인한다. 봇 위임과 API 토큰은 소유자가 관리자여도 거부한다.
 */
public final class AdminAccess {

    private AdminAccess() {}

    public static void require(CustomUserDetails user) {
        if (user == null
                || user instanceof BotDelegatedUserDetails
                || user instanceof ApiTokenUserDetails
                || user.getMember() == null
                || user.getMember().getRole() != Role.ROLE_ADMIN) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }
}
