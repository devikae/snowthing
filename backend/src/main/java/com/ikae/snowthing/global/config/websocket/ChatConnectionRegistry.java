package com.ikae.snowthing.global.config.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.ikae.snowthing.global.security.CustomUserDetails;

@Component
public class ChatConnectionRegistry {

    static final CloseStatus MEMBER_REPLACED = new CloseStatus(4001, "MEMBER_CONNECTION_REPLACED");
    static final CloseStatus SERVER_CAPACITY = new CloseStatus(4002, "CHAT_SERVER_CAPACITY");
    static final CloseStatus MEMBER_LOGOUT = new CloseStatus(4003, "MEMBER_LOGOUT");

    private final int maxConnections;
    private final Map<String, WebSocketSession> sessionsById = new HashMap<>();
    private final Map<Long, String> sessionIdByMemberId = new HashMap<>();
    private final Map<String, String> sessionIdByHttpSessionId = new HashMap<>();

    public ChatConnectionRegistry(
            @Value("${snowthing.chat.max-connections:500}") int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public RegistrationResult register(WebSocketSession session) {
        WebSocketSession replacedSession;
        synchronized (this) {
            if (sessionsById.containsKey(session.getId())) {
                return RegistrationResult.accepted(null);
            }
            Long memberId = resolveMemberId(session);
            String existingMemberSessionId =
                    memberId != null ? sessionIdByMemberId.get(memberId) : null;
            if (sessionsById.size() >= maxConnections && existingMemberSessionId == null) {
                return RegistrationResult.rejected();
            }

            sessionsById.put(session.getId(), session);
            String httpSessionId =
                    (String)
                            session.getAttributes()
                                    .get(ChatHandshakeInterceptor.ATTR_HTTP_SESSION_ID);
            if (httpSessionId != null) {
                sessionIdByHttpSessionId.put(httpSessionId, session.getId());
            }

            String replacedSessionId =
                    memberId != null ? sessionIdByMemberId.put(memberId, session.getId()) : null;
            replacedSession = detachIfDifferent(replacedSessionId, session.getId());
        }
        return RegistrationResult.accepted(replacedSession);
    }

    public void unregister(String sessionId) {
        synchronized (this) {
            detach(sessionId);
        }
    }

    public void disconnectHttpSession(String httpSessionId) {
        WebSocketSession session;
        synchronized (this) {
            String webSocketSessionId = sessionIdByHttpSessionId.get(httpSessionId);
            session = detach(webSocketSessionId);
        }
        closeQuietly(session, MEMBER_LOGOUT);
    }

    public int connectionCount() {
        synchronized (this) {
            return sessionsById.size();
        }
    }

    private WebSocketSession detachIfDifferent(String existingSessionId, String newSessionId) {
        if (existingSessionId == null || existingSessionId.equals(newSessionId)) {
            return null;
        }
        return detach(existingSessionId);
    }

    private WebSocketSession detach(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        WebSocketSession removed = sessionsById.remove(sessionId);
        if (removed == null) {
            return null;
        }

        Long memberId = resolveMemberId(removed);
        if (memberId != null) {
            sessionIdByMemberId.remove(memberId, sessionId);
        }
        String httpSessionId =
                (String) removed.getAttributes().get(ChatHandshakeInterceptor.ATTR_HTTP_SESSION_ID);
        if (httpSessionId != null) {
            sessionIdByHttpSessionId.remove(httpSessionId, sessionId);
        }
        return removed;
    }

    private Long resolveMemberId(WebSocketSession session) {
        if (session.getPrincipal() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails
                && userDetails.getMember() != null) {
            return userDetails.getMember().getId();
        }
        return null;
    }

    static void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException ignored) {
            // The disconnect event performs the same cleanup when transport closure is observed.
        }
    }

    public record RegistrationResult(boolean accepted, WebSocketSession replacedSession) {

        static RegistrationResult accepted(WebSocketSession replacedSession) {
            return new RegistrationResult(true, replacedSession);
        }

        static RegistrationResult rejected() {
            return new RegistrationResult(false, null);
        }
    }
}
