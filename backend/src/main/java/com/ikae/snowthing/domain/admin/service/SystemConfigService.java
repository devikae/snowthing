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
import com.ikae.snowthing.domain.admin.repository.SystemConfigRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SystemConfigService {

    public static final String KEY_CHAT_ANONYMOUS_ENABLED = "CHAT_ANONYMOUS_ENABLED";

    private final SystemConfigRepository repository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Cache<String, String> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build();

    public SystemConfigService(
            SystemConfigRepository repository, @Lazy SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.messagingTemplate = messagingTemplate;
    }

    public boolean isAnonymousChatEnabled() {
        String val =
                cache.get(
                        KEY_CHAT_ANONYMOUS_ENABLED,
                        key ->
                                repository
                                        .findById(key)
                                        .map(SystemConfig::getValue)
                                        .orElse("false"));
        return Boolean.parseBoolean(val);
    }

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> getAllConfigs() {
        return List.copyOf(
                repository.findAll().stream().map(SystemConfigResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublicConfigs() {
        return Map.of("anonymousChatEnabled", isAnonymousChatEnabled());
    }

    @Transactional
    public SystemConfigResponse updateConfig(String key, String value, String updatedBy) {
        SystemConfig config =
                repository
                        .findById(key)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONFIG_NOT_FOUND));

        config.update(value, updatedBy);
        cache.put(key, value);
        log.info("System config updated: key={}, value={}, by={}", key, value, updatedBy);

        if (KEY_CHAT_ANONYMOUS_ENABLED.equals(key)) {
            try {
                messagingTemplate.convertAndSend(
                        "/sub/chat/main",
                        (Object) Map.of(
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
