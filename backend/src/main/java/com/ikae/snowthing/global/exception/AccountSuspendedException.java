package com.ikae.snowthing.global.exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.ikae.snowthing.global.error.ErrorCode;

import lombok.Getter;

/**
 * 정지 계정 로그인 거절 (IKA-115). 기간이 정해진 정지면 풀리는 시각을 문구에 넣는다. 사유는 관리자 화면에만 두고 여기에는 넣지 않는다.
 *
 * <p>본인 확인(비밀번호 일치, 소셜 인증)을 마친 뒤에만 던진다. 그 전에 알려 주면 비밀번호를 몰라도 계정 상태를 알 수 있다.
 */
@Getter
public class AccountSuspendedException extends CustomException {

    private static final DateTimeFormatter UNTIL_FORMAT =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN);

    /** 풀리는 시각(KST). 무기한이면 null. */
    private final LocalDateTime suspendedUntil;

    public AccountSuspendedException(LocalDateTime suspendedUntil) {
        super(ErrorCode.ACCOUNT_SUSPENDED, messageOf(suspendedUntil));
        this.suspendedUntil = suspendedUntil;
    }

    static String messageOf(LocalDateTime until) {
        String base = ErrorCode.ACCOUNT_SUSPENDED.getMessage();
        return until == null ? base : base + " " + until.format(UNTIL_FORMAT) + "에 풀립니다.";
    }
}
