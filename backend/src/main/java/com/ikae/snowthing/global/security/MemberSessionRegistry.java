package com.ikae.snowthing.global.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry;
import com.ikae.snowthing.global.config.websocket.ChatSessionRevocationRegistry;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MemberSessionRegistry implements HttpSessionListener {

    private final ChatConnectionRegistry chatConnectionRegistry;
    private final ChatSessionRevocationRegistry chatSessionRevocationRegistry;
    private final RememberMeTokenRepository rememberMeTokenRepository;
    private final ConcurrentHashMap<String, Set<HttpSession>> sessionsByMember =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<HttpSession, String> memberBySession =
            new ConcurrentHashMap<>();

    public void register(String memberPublicId, HttpSession session) {
        String previousMemberPublicId = memberBySession.put(session, memberPublicId);
        if (previousMemberPublicId != null && !previousMemberPublicId.equals(memberPublicId)) {
            removeFromMember(previousMemberPublicId, session);
        }
        sessionsByMember
                .computeIfAbsent(memberPublicId, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
    }

    public void unregister(String memberPublicId, HttpSession session) {
        memberBySession.remove(session, memberPublicId);
        removeFromMember(memberPublicId, session);
    }

    private void removeFromMember(String memberPublicId, HttpSession session) {
        Set<HttpSession> sessions = sessionsByMember.get(memberPublicId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByMember.remove(memberPublicId, sessions);
        }
    }

    /**
     * 회원의 모든 세션을 끊고 로그인 유지 토큰도 지운다. 토큰이 남으면 끊긴 기기가 쿠키로 다시 로그인한다. 정지, 권한 변경, 비밀번호 변경과 재설정, 탈퇴가 모두 이
     * 메서드를 거친다.
     */
    public void invalidateAll(String memberPublicId) {
        invalidateSessions(memberPublicId);
        rememberMeTokenRepository.deleteByMember(memberPublicId);
    }

    private void invalidateSessions(String memberPublicId) {
        Set<HttpSession> sessions = sessionsByMember.remove(memberPublicId);
        if (sessions == null) {
            return;
        }
        for (HttpSession session : sessions) {
            try {
                memberBySession.remove(session, memberPublicId);
                String sessionId = session.getId();
                chatConnectionRegistry.disconnectHttpSession(sessionId);
                chatSessionRevocationRegistry.revoke(sessionId);
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // The container already expired this session.
            }
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        String memberPublicId = memberBySession.remove(event.getSession());
        if (memberPublicId != null) {
            removeFromMember(memberPublicId, event.getSession());
        }
    }
}
