package com.ikae.snowthing.global.config.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import com.ikae.snowthing.domain.chat.audit.ChatAuditEvent;
import com.ikae.snowthing.domain.chat.audit.ChatAuditLogger;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.security.CustomUserDetails;

class ChatConnectionWebSocketHandlerDecoratorTest {

    private static final int MAX_CONNECTIONS = 2;
    private static final long MEMBER_ID = 101L;
    private static final String SESSION_ID = "websocket-session-1";
    private static final String CLIENT_IP = "203.0.113.10";
    private static final String CHANNEL = "MAIN_CHAT";

    @Test
    @DisplayName("수락된 채팅 연결과 종료를 메시지 본문 없이 세션 단위로 감사 기록한다")
    void acceptedConnectionAndDisconnectAreAudited() throws Exception {
        WebSocketHandler delegate = mock(WebSocketHandler.class);
        ChatAuditLogger auditLogger = mock(ChatAuditLogger.class);
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        ChatConnectionWebSocketHandlerDecorator decorator =
                new ChatConnectionWebSocketHandlerDecorator(delegate, registry, auditLogger);
        WebSocketSession session = memberSession();

        decorator.afterConnectionEstablished(session);
        decorator.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(auditLogger)
                .log(ChatAuditEvent.CONNECT, MEMBER_ID, CLIENT_IP, CHANNEL, SESSION_ID, null);
        verify(auditLogger)
                .log(
                        ChatAuditEvent.DISCONNECT,
                        MEMBER_ID,
                        CLIENT_IP,
                        CHANNEL,
                        SESSION_ID,
                        CloseStatus.NORMAL.toString());
    }

    private WebSocketSession memberSession() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(MEMBER_ID);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getMember()).thenReturn(member);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.getPrincipal()).thenReturn(authentication);
        when(session.getAttributes())
                .thenReturn(
                        new HashMap<>(Map.of(ChatHandshakeInterceptor.ATTR_CLIENT_IP, CLIENT_IP)));
        return session;
    }
}
