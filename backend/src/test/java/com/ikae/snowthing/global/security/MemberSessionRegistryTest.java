package com.ikae.snowthing.global.security;

import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry;
import com.ikae.snowthing.global.config.websocket.ChatSessionRevocationRegistry;

class MemberSessionRegistryTest {

    private ChatConnectionRegistry chatConnectionRegistry;
    private ChatSessionRevocationRegistry revocationRegistry;
    private MemberSessionRegistry registry;

    @BeforeEach
    void setUp() {
        chatConnectionRegistry = mock(ChatConnectionRegistry.class);
        revocationRegistry = mock(ChatSessionRevocationRegistry.class);
        registry = new MemberSessionRegistry(chatConnectionRegistry, revocationRegistry);
    }

    @Test
    void passwordResetInvalidatesEveryRegisteredSessionAndChatConnection() {
        HttpSession first = session("session-1");
        HttpSession second = session("session-2");
        registry.register("member-1", first);
        registry.register("member-1", second);

        registry.invalidateAll("member-1");

        verify(first).invalidate();
        verify(second).invalidate();
        verify(chatConnectionRegistry).disconnectHttpSession("session-1");
        verify(chatConnectionRegistry).disconnectHttpSession("session-2");
        verify(revocationRegistry).revoke("session-1");
        verify(revocationRegistry).revoke("session-2");
    }

    @Test
    void sameHttpSessionReLoginMovesOwnershipToTheNewMember() {
        HttpSession session = session("changed-session-id");
        registry.register("member-1", session);
        registry.register("member-2", session);

        registry.invalidateAll("member-1");
        verify(session, never()).invalidate();

        registry.invalidateAll("member-2");
        verify(session).invalidate();
    }

    @Test
    void containerExpirationRemovesSessionFromMemberIndex() {
        HttpSession session = session("expired-session");
        registry.register("member-1", session);

        registry.sessionDestroyed(new HttpSessionEvent(session));
        registry.invalidateAll("member-1");

        verify(session, never()).invalidate();
    }

    private HttpSession session(String id) {
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }
}
