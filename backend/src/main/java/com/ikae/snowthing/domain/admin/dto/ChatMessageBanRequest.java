package com.ikae.snowthing.domain.admin.dto;

import java.util.Set;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 라이브톡 메시지로 IP를 차단할 때 차단 기간(일). 본문을 보내지 않거나 days를 비우면 예전과 같이 7일이다 (IKA-89). 화면에서 고르는 1일, 7일, 30일만
 * 받는다.
 */
public record ChatMessageBanRequest(Integer days) {

    public static final int DEFAULT_DAYS = 7;
    private static final Set<Integer> ALLOWED_DAYS = Set.of(1, 7, 30);

    public static int resolveDays(ChatMessageBanRequest request) {
        if (request == null || request.days() == null) {
            return DEFAULT_DAYS;
        }
        if (!ALLOWED_DAYS.contains(request.days())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return request.days();
    }
}
