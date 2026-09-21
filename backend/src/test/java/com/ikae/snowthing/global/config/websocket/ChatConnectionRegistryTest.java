package com.ikae.snowthing.global.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketSession;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry.RegistrationResult;
import com.ikae.snowthing.global.security.CustomUserDetails;

class ChatConnectionRegistryTest {

    private static final int MAX_CONNECTIONS = 2;
    private static final long MEMBER_ID = 101L;
    private static final String FIRST_SESSION_ID = "websocket-session-1";
    private static final String SECOND_SESSION_ID = "websocket-session-2";
    private static final String THIRD_SESSION_ID = "websocket-session-3";
    private static final String HTTP_SESSION_ID = "http-session-1";

    @Test
    @DisplayName("같은 회원이 다시 연결하면 새 연결을 등록하고 기존 연결을 교체 대상으로 반환한다")
    void sameMemberConnectionReplacesPreviousSession() {
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        WebSocketSession first = memberSession(FIRST_SESSION_ID, MEMBER_ID, HTTP_SESSION_ID);
        WebSocketSession second = memberSession(SECOND_SESSION_ID, MEMBER_ID, HTTP_SESSION_ID);

        RegistrationResult firstResult = registry.register(first);
        RegistrationResult secondResult = registry.register(second);

        assertThat(firstResult.accepted()).isTrue();
        assertThat(secondResult.accepted()).isTrue();
        assertThat(secondResult.replacedSession()).isSameAs(first);
        assertThat(registry.connectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("서버 전체 연결 한도를 넘는 신규 연결은 거부한다")
    void connectionOverServerCapacityIsRejected() {
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        registry.register(anonymousSession(FIRST_SESSION_ID));
        registry.register(anonymousSession(SECOND_SESSION_ID));

        RegistrationResult result = registry.register(anonymousSession(THIRD_SESSION_ID));

        assertThat(result.accepted()).isFalse();
        assertThat(registry.connectionCount()).isEqualTo(MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("익명 연결을 해제하면 전체 연결 제한에서 자리가 반환된다")
    void unregisterAnonymousSessionReleasesServerCapacity() {
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        registry.register(anonymousSession(FIRST_SESSION_ID));
        registry.register(anonymousSession(SECOND_SESSION_ID));

        registry.unregister(FIRST_SESSION_ID);
        RegistrationResult result = registry.register(anonymousSession(THIRD_SESSION_ID));

        assertThat(result.accepted()).isTrue();
        assertThat(registry.connectionCount()).isEqualTo(MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("서버가 가득 차도 이미 연결된 회원의 새 연결은 기존 연결과 교체된다")
    void existingMemberCanReplaceConnectionAtCapacity() {
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        WebSocketSession first = memberSession(FIRST_SESSION_ID, MEMBER_ID, HTTP_SESSION_ID);
        registry.register(first);
        registry.register(anonymousSession(SECOND_SESSION_ID));

        RegistrationResult result =
                registry.register(memberSession(THIRD_SESSION_ID, MEMBER_ID, HTTP_SESSION_ID));

        assertThat(result.accepted()).isTrue();
        assertThat(result.replacedSession()).isSameAs(first);
        assertThat(registry.connectionCount()).isEqualTo(MAX_CONNECTIONS);
    }

    @Test
    @DisplayName("HTTP 로그아웃 시 연결된 WebSocket을 종료하고 등록에서 제거한다")
    void logoutDisconnectsRegisteredWebSocket() throws Exception {
        ChatConnectionRegistry registry = new ChatConnectionRegistry(MAX_CONNECTIONS);
        WebSocketSession session = memberSession(FIRST_SESSION_ID, MEMBER_ID, HTTP_SESSION_ID);
        registry.register(session);

        registry.disconnectHttpSession(HTTP_SESSION_ID);

        verify(session).close(ChatConnectionRegistry.MEMBER_LOGOUT);
        assertThat(registry.connectionCount()).isZero();
    }

    private WebSocketSession memberSession(String sessionId, long memberId, String httpSessionId) {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(memberId);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getMember()).thenReturn(member);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        WebSocketSession session = baseSession(sessionId);
        when(session.getPrincipal()).thenReturn(authentication);
        when(session.getAttributes())
                .thenReturn(
                        new HashMap<>(
                                Map.of(
                                        ChatHandshakeInterceptor.ATTR_HTTP_SESSION_ID,
                                        httpSessionId)));
        return session;
    }

    private WebSocketSession anonymousSession(String sessionId) {
        WebSocketSession session = baseSession(sessionId);
        when(session.getPrincipal()).thenReturn(mock(Principal.class));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        return session;
    }

    private WebSocketSession baseSession(String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
