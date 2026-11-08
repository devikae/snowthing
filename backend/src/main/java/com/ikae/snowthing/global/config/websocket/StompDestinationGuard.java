package com.ikae.snowthing.global.config.websocket;

import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 클라이언트가 보낸 STOMP 프레임의 목적지를 제한한다.
 *
 * <p>SimpleBroker는 브로커 경로(/sub, /queue)로 직접 온 SEND도 구독자에게 그대로 전달한다. 그러면 ChatController의 회원 확인, 도배
 * 제한, 금칙어 검사를 모두 건너뛰므로 SEND는 애플리케이션 경로(/pub)만 받는다. 구독도 화면이 쓰는 경로만 허용해 다른 사람의 개인 큐를 엿보지 못하게 한다.
 */
@Slf4j
@Component
public class StompDestinationGuard implements ChannelInterceptor {

    private static final String APPLICATION_PREFIX = "/pub/";
    private static final Set<String> ALLOWED_SUBSCRIPTIONS =
            Set.of("/sub/chat/main", "/user/queue/errors");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();
        if (command == StompCommand.SEND && !isApplicationDestination(destination)) {
            return reject(accessor, destination);
        }
        if (command == StompCommand.SUBSCRIBE && !ALLOWED_SUBSCRIPTIONS.contains(destination)) {
            return reject(accessor, destination);
        }
        return message;
    }

    private boolean isApplicationDestination(String destination) {
        return destination != null
                && destination.startsWith(APPLICATION_PREFIX)
                && !destination.contains("..");
    }

    private Message<?> reject(StompHeaderAccessor accessor, String destination) {
        log.warn(
                "Rejected STOMP {} to destination={} sessionId={}",
                accessor.getCommand(),
                safeForLog(destination),
                accessor.getSessionId());
        return null;
    }

    private String safeForLog(String destination) {
        if (destination == null) {
            return "null";
        }
        String flat = destination.replaceAll("[\r\n\t]", "_");
        return flat.length() > 100 ? flat.substring(0, 100) : flat;
    }
}
