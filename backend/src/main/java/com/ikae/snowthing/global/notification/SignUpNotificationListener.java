package com.ikae.snowthing.global.notification;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;

/** 커밋된 가입만 #이벤트 채널로 알린다. 롤백된 가입은 AFTER_COMMIT이 호출되지 않는다. */
@Component
public class SignUpNotificationListener {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final DiscordNotifier notifier;
    private final Clock clock;

    @Autowired
    public SignUpNotificationListener(DiscordNotifier notifier) {
        this(notifier, Clock.systemUTC());
    }

    SignUpNotificationListener(DiscordNotifier notifier, Clock clock) {
        this.notifier = notifier;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSignedUp(MemberSignedUpEvent event) {
        notifier.event(
                "🎉 신규 가입: "
                        + event.nickname()
                        + " ("
                        + event.method()
                        + ") "
                        + ZonedDateTime.now(clock).withZoneSameInstant(KST).format(TIME));
    }
}
