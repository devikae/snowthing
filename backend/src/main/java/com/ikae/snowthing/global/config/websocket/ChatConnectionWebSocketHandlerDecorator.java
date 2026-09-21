package com.ikae.snowthing.global.config.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import com.ikae.snowthing.domain.chat.audit.ChatAuditEvent;
import com.ikae.snowthing.domain.chat.audit.ChatAuditLogger;
import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry.RegistrationResult;
import com.ikae.snowthing.global.security.CustomUserDetails;

public class ChatConnectionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    private static final String AUDIT_CHANNEL = "MAIN_CHAT";

    private final ChatConnectionRegistry connectionRegistry;
    private final ChatAuditLogger chatAuditLogger;

    public ChatConnectionWebSocketHandlerDecorator(
            WebSocketHandler delegate,
            ChatConnectionRegistry connectionRegistry,
            ChatAuditLogger chatAuditLogger) {
        super(delegate);
        this.connectionRegistry = connectionRegistry;
        this.chatAuditLogger = chatAuditLogger;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        RegistrationResult result = connectionRegistry.register(session);
        if (!result.accepted()) {
            ChatConnectionRegistry.closeQuietly(session, ChatConnectionRegistry.SERVER_CAPACITY);
            return;
        }
        logConnectionEvent(ChatAuditEvent.CONNECT, session, null);
        ChatConnectionRegistry.closeQuietly(
                result.replacedSession(), ChatConnectionRegistry.MEMBER_REPLACED);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
            throws Exception {
        connectionRegistry.unregister(session.getId());
        logConnectionEvent(ChatAuditEvent.DISCONNECT, session, closeStatus.toString());
        super.afterConnectionClosed(session, closeStatus);
    }

    private void logConnectionEvent(
            ChatAuditEvent event, WebSocketSession session, String closeReason) {
        chatAuditLogger.log(
                event,
                resolveMemberId(session),
                (String) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_CLIENT_IP),
                AUDIT_CHANNEL,
                session.getId(),
                closeReason);
    }

    private Long resolveMemberId(WebSocketSession session) {
        if (session.getPrincipal() instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getMember() != null) {
            return userDetails.getMember().getId();
        }
        return null;
    }
}
