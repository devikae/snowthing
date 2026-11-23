package com.ikae.snowthing.global.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;

class SignUpNotificationListenerTest {

    // 09:33 UTC = 18:33 KST
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T09:33:00Z"), ZoneOffset.UTC);
    private final DiscordNotifier notifier = mock(DiscordNotifier.class);
    private final SignUpNotificationListener listener =
            new SignUpNotificationListener(notifier, clock);

    @Test
    @DisplayName("소셜 가입은 닉네임, 소셜 이름, 제공자를 한글로 보낸다")
    void socialSignUp() {
        listener.onSignedUp(new MemberSignedUpEvent("꿋꿋한부엉이03", "KAKAO", "홍길동"));

        verify(notifier).event("🎉 신규 가입 18:33\n닉네임: 꿋꿋한부엉이03\n이름: 홍길동\n가입: 카카오");
    }

    @Test
    @DisplayName("이메일 가입이나 소셜 이름이 비어 있으면 이름을 없음으로 보낸다")
    void noSocialName() {
        listener.onSignedUp(new MemberSignedUpEvent("신규보더", "EMAIL", null));
        listener.onSignedUp(new MemberSignedUpEvent("구글보더", "GOOGLE", " "));

        verify(notifier).event("🎉 신규 가입 18:33\n닉네임: 신규보더\n이름: 없음\n가입: 이메일");
        verify(notifier).event("🎉 신규 가입 18:33\n닉네임: 구글보더\n이름: 없음\n가입: 구글");
    }
}
