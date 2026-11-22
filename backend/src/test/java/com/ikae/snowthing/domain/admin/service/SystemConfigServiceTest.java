package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.ikae.snowthing.domain.admin.dto.SystemConfigResponse;
import com.ikae.snowthing.domain.admin.entity.SystemConfig;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.repository.SystemConfigRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository repository;

    @Mock private SimpMessagingTemplate messagingTemplate;

    @Mock private AdminActionLogger actionLogger;

    private SystemConfigService service;

    @BeforeEach
    void setUp() {
        service = new SystemConfigService(repository, messagingTemplate, actionLogger);
    }

    @Test
    @DisplayName("비로그인 채팅 설정 조회 - 기본값 false")
    void isAnonymousChatEnabled_defaultFalse() {
        when(repository.findById("CHAT_ANONYMOUS_ENABLED")).thenReturn(Optional.empty());
        assertThat(service.isAnonymousChatEnabled()).isFalse();
    }

    @Test
    @DisplayName("AI 회원 스위치는 설정 행이 없으면 켜진 것으로 본다")
    void aiMemberSwitches_defaultTrue() {
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertThat(service.isAiMemberPostEnabled()).isTrue();
        assertThat(service.isAiMemberCommentEnabled()).isTrue();
        assertThat(service.isAiMemberChatEnabled()).isTrue();
    }

    @Test
    @DisplayName("AI 회원 스위치를 끄면 바로 꺼진 값으로 읽힌다")
    void aiMemberSwitch_updateTakesEffect() {
        SystemConfig config =
                new SystemConfig("AI_MEMBER_POST_ENABLED", "true", "BOOLEAN", "AI 회원 글쓰기 허용 여부", "SYSTEM");
        when(repository.findById("AI_MEMBER_POST_ENABLED")).thenReturn(Optional.of(config));
        assertThat(service.isAiMemberPostEnabled()).isTrue();

        service.updateConfig("AI_MEMBER_POST_ENABLED", "false", "admin");

        assertThat(service.isAiMemberPostEnabled()).isFalse();
    }

    @Test
    @DisplayName("설정 업데이트 성공 및 캐시 갱신")
    void updateConfig_success() {
        SystemConfig config =
                new SystemConfig(
                        "CHAT_ANONYMOUS_ENABLED", "false", "BOOLEAN", "비로그인 채팅 허용 여부", "SYSTEM");

        when(repository.findById("CHAT_ANONYMOUS_ENABLED")).thenReturn(Optional.of(config));

        SystemConfigResponse res = service.updateConfig("CHAT_ANONYMOUS_ENABLED", "true", "admin");

        assertThat(res.value()).isEqualTo("true");
        assertThat(service.isAnonymousChatEnabled()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 설정 키 업데이트 시 예외 발생")
    void updateConfig_notFound() {
        when(repository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConfig("UNKNOWN", "val", "admin"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFIG_NOT_FOUND);
    }
}
