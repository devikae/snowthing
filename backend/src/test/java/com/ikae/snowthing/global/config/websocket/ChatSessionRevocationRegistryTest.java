package com.ikae.snowthing.global.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatSessionRevocationRegistryTest {

    private static final String SESSION_ID = "chat-session-id";

    private final ChatSessionRevocationRegistry registry = new ChatSessionRevocationRegistry();

    @Test
    @DisplayName("로그아웃한 HTTP 세션은 라이브톡 세션에서도 폐기 상태로 조회된다")
    void revokedSessionIsRejected() {
        assertThat(registry.isRevoked(SESSION_ID)).isFalse();

        registry.revoke(SESSION_ID);

        assertThat(registry.isRevoked(SESSION_ID)).isTrue();
    }

    @Test
    @DisplayName("세션 ID가 없으면 폐기 목록에 추가하지 않는다")
    void missingSessionIdIsIgnored() {
        registry.revoke(null);
        registry.revoke(" ");

        assertThat(registry.isRevoked(null)).isFalse();
        assertThat(registry.isRevoked(" ")).isFalse();
    }
}
