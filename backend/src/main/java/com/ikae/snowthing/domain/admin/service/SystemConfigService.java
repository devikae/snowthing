package com.ikae.snowthing.domain.admin.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.admin.dto.SystemConfigResponse;
import com.ikae.snowthing.domain.admin.entity.SystemConfig;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.SystemConfigRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SystemConfigService {

    public static final String KEY_CHAT_ANONYMOUS_ENABLED = "CHAT_ANONYMOUS_ENABLED";
    public static final String KEY_MARKET_GUEST_ACCESS_ENABLED = "MARKET_GUEST_ACCESS_ENABLED";
    // AI 회원 활동 스위치(IKA-108). 관리자 화면 전용이라 공개 설정에는 넣지 않는다.
    public static final String KEY_AI_MEMBER_POST_ENABLED = "AI_MEMBER_POST_ENABLED";
    public static final String KEY_AI_MEMBER_COMMENT_ENABLED = "AI_MEMBER_COMMENT_ENABLED";
    public static final String KEY_AI_MEMBER_CHAT_ENABLED = "AI_MEMBER_CHAT_ENABLED";
    // 쪽지 도착 메일 스위치(IKA-109). 관리자 화면 전용이라 공개 설정에는 넣지 않는다.
    public static final String KEY_DM_MAIL_ENQUEUE_ENABLED = "DM_MAIL_ENQUEUE_ENABLED";
    public static final String KEY_DM_MAIL_SEND_ENABLED = "DM_MAIL_SEND_ENABLED";

    private final SystemConfigRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActionLogger actionLogger;
    private final Cache<String, String> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();

    public SystemConfigService(
            SystemConfigRepository repository,
            @Lazy SimpMessagingTemplate messagingTemplate,
            AdminActionLogger actionLogger) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
        this.actionLogger = actionLogger;
    }

    public boolean isAnonymousChatEnabled() {
        return readBoolean(KEY_CHAT_ANONYMOUS_ENABLED, false);
    }

    /** 중고장터 목록과 상세를 비회원도 볼 수 있는지. 설정 행이 없으면 허용으로 본다. */
    public boolean isMarketGuestAccessEnabled() {
        return readBoolean(KEY_MARKET_GUEST_ACCESS_ENABLED, true);
    }

    /** AI 회원 글쓰기. 설정 행이 없으면 켜진 것으로 본다. */
    public boolean isAiMemberPostEnabled() {
        return readBoolean(KEY_AI_MEMBER_POST_ENABLED, true);
    }

    /** AI 회원 댓글. 설정 행이 없으면 켜진 것으로 본다. */
    public boolean isAiMemberCommentEnabled() {
        return readBoolean(KEY_AI_MEMBER_COMMENT_ENABLED, true);
    }

    /** AI 회원 라이브톡 대화. 설정 행이 없으면 켜진 것으로 본다. */
    public boolean isAiMemberChatEnabled() {
        return readBoolean(KEY_AI_MEMBER_CHAT_ENABLED, true);
    }

    /** 쪽지 메일 발송 요청 기록. 설정 행이 없으면 꺼진 것으로 본다. 환경변수까지 합친 최종 판단은 {@code DmMailSwitches}가 한다. */
    public boolean isDmMailEnqueueEnabled() {
        return readBoolean(KEY_DM_MAIL_ENQUEUE_ENABLED, false);
    }

    /** 쪽지 메일 발송. 설정 행이 없으면 꺼진 것으로 본다. */
    public boolean isDmMailSendEnabled() {
        return readBoolean(KEY_DM_MAIL_SEND_ENABLED, false);
    }

    private boolean readBoolean(String configKey, boolean defaultValue) {
        String val =
                cache.get(
                        configKey,
                        key ->
                                repository
                                        .findById(key)
                                        .map(SystemConfig::getValue)
                                        .orElse(String.valueOf(defaultValue)));
        return Boolean.parseBoolean(val);
    }

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> getAllConfigs() {
        return List.copyOf(repository.findAll().stream().map(SystemConfigResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicConfigs() {
        return Map.of(
                "anonymousChatEnabled",
                isAnonymousChatEnabled(),
                "marketGuestAccessEnabled",
                isMarketGuestAccessEnabled());
    }

    @Transactional
    public SystemConfigResponse updateConfig(String key, String value, String updatedBy) {
        SystemConfig config =
                repository
                        .findById(key)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONFIG_NOT_FOUND));

        config.update(value, updatedBy);
        actionLogger.record(AdminActionType.CONFIG_UPDATE, AdminTargetType.CONFIG, key, value);
        cache.put(key, value);
        log.info("System config updated: key={}, value={}, by={}", key, value, updatedBy);

        if (KEY_CHAT_ANONYMOUS_ENABLED.equals(key)) {
            try {
                messagingTemplate.convertAndSend(
                        "/sub/chat/main",
                        (Object)
                                Map.of(
                                        "type",
                                        "CONFIG_UPDATED",
                                        "key",
                                        key,
                                        "value",
                                        Boolean.parseBoolean(value)));
            } catch (Exception e) {
                log.warn("Failed to broadcast config update via websocket: {}", e.getMessage());
            }
        }

        return SystemConfigResponse.from(config);
    }
}
