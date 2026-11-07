package com.ikae.snowthing.global.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompDestinationGuardTest {

    private final StompDestinationGuard guard = new StompDestinationGuard();

    @Test
    @DisplayName("애플리케이션 경로로 보내는 SEND는 통과한다")
    void allowsApplicationSend() {
        Message<byte[]> message = frame(StompCommand.SEND, "/pub/chat/messages");

        assertThat(guard.preSend(message, null)).isSameAs(message);
    }

    @Test
    @DisplayName("브로커 경로로 직접 보내는 SEND는 버린다")
    void rejectsBrokerSend() {
        assertThat(guard.preSend(frame(StompCommand.SEND, "/sub/chat/main"), null)).isNull();
        assertThat(guard.preSend(frame(StompCommand.SEND, "/queue/errors"), null)).isNull();
        assertThat(guard.preSend(frame(StompCommand.SEND, "/user/queue/errors"), null)).isNull();
        assertThat(guard.preSend(frame(StompCommand.SEND, "/pub/../sub/chat/main"), null)).isNull();
        assertThat(guard.preSend(frame(StompCommand.SEND, null), null)).isNull();
    }

    @Test
    @DisplayName("화면이 쓰는 구독 경로만 허용한다")
    void allowsOnlyKnownSubscriptions() {
        Message<byte[]> main = frame(StompCommand.SUBSCRIBE, "/sub/chat/main");
        Message<byte[]> errors = frame(StompCommand.SUBSCRIBE, "/user/queue/errors");

        assertThat(guard.preSend(main, null)).isSameAs(main);
        assertThat(guard.preSend(errors, null)).isSameAs(errors);
        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "/queue/errors-user1"), null))
                .isNull();
        assertThat(guard.preSend(frame(StompCommand.SUBSCRIBE, "/sub/chat/other"), null)).isNull();
    }

    @Test
    @DisplayName("연결과 해제 같은 다른 프레임은 그대로 둔다")
    void ignoresOtherCommands() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null);

        assertThat(guard.preSend(connect, null)).isSameAs(connect);
    }

    private Message<byte[]> frame(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("s1");
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
