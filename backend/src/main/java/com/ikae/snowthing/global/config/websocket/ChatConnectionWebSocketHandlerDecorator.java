package com.ikae.snowthing.global.config.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry.RegistrationResult;

public class ChatConnectionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    private final ChatConnectionRegistry connectionRegistry;

    public ChatConnectionWebSocketHandlerDecorator(
            WebSocketHandler delegate, ChatConnectionRegistry connectionRegistry) {
        super(delegate);
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        RegistrationResult result = connectionRegistry.register(session);
        if (!result.accepted()) {
            ChatConnectionRegistry.closeQuietly(session, ChatConnectionRegistry.SERVER_CAPACITY);
            return;
        }
        ChatConnectionRegistry.closeQuietly(
                result.replacedSession(), ChatConnectionRegistry.MEMBER_REPLACED);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
            throws Exception {
        connectionRegistry.unregister(session.getId());
        super.afterConnectionClosed(session, closeStatus);
    }
}
