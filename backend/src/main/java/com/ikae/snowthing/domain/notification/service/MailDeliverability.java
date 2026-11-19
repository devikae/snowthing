package com.ikae.snowthing.domain.notification.service;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 회원 이메일로 실제 메일을 보낼 수 있는지 판단한다.
 *
 * <p>인증되지 않은 주소와 {@code .invalid} 도메인은 받을 수 없는 주소로 본다. 소셜 가입에서 제공자가 이메일을 주지 않으면 {@code
 * users.invalid} 도메인의 내부 대체 주소를 만들고, 탈퇴하면 {@code withdrawn.invalid} 도메인으로 바꾼다.
 */
public final class MailDeliverability {

    private MailDeliverability() {}

    public static boolean isDeliverable(String email, LocalDateTime emailVerifiedAt) {
        if (email == null || emailVerifiedAt == null) {
            return false;
        }
        return !email.toLowerCase(Locale.ROOT).endsWith(".invalid");
    }
}
