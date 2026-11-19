package com.ikae.snowthing.domain.dmmail.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.MailTarget;
import com.ikae.snowthing.domain.message.entity.DirectMessage;
import com.ikae.snowthing.domain.notification.service.MailDeliverability;
import com.ikae.snowthing.domain.notification.service.NotificationSettingService;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 저장과 같은 트랜잭션에서 메일 발송 요청을 쓴다.
 *
 * <p>MANDATORY라서 트랜잭션 밖에서 부르면 바로 실패한다. 쪽지가 롤백되면 발송 요청도 함께 사라지고, 커밋되면 반드시 남는다. 발송 요청 INSERT가 실패하면
 * 쪽지도 롤백된다. 받는 사람이 알림을 꺼 두었으면 행을 만들지 않는다. 적재 이후에 끈 경우는 발송 직전 확인에서 걸러진다.
 */
@Component
@RequiredArgsConstructor
public class DmMailOutboxWriter {

    private final DmMailOutboxRepository outbox;
    private final NotificationSettingService settings;
    private final DmMailProperties properties;
    private final DmMailSender sender;
    private final DmMailComposer composer;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void messageSent(DirectMessage message) {
        if (!properties.enqueueEnabled()) {
            return;
        }
        Long receiverId = message.getReceiver().getId();
        if (!settings.isDmEmailEnabled(receiverId)) {
            return;
        }
        if (properties.syncBaseline()) {
            sendInsideTransaction(message);
            return;
        }
        outbox.insertIfAbsent(
                message.getId(),
                receiverId,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    /**
     * 성능 비교 기준선 전용. 쪽지 트랜잭션 안에서 SES를 바로 부른다. SES가 느리면 쪽지 응답과 DB 커넥션이 그만큼 묶이고, 메일이 나간 뒤 쪽지가 롤백될 수
     * 있다. 운영에서 쓰지 않는다.
     */
    private void sendInsideTransaction(DirectMessage message) {
        var receiver = message.getReceiver();
        if (!MailDeliverability.isDeliverable(receiver.getEmail(), receiver.getEmailVerifiedAt())) {
            return;
        }
        var target =
                new MailTarget(
                        0L,
                        message.getCreatedAt(),
                        false,
                        message.getSender().getNickname(),
                        message.getSender().getStatus().name(),
                        receiver.getEmail(),
                        receiver.getEmailVerifiedAt(),
                        receiver.getStatus().name(),
                        true);
        sender.send(composer.compose(receiver.getEmail(), List.of(target)));
    }
}
