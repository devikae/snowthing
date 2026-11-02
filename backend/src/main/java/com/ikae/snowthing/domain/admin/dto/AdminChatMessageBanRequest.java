package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

public record AdminChatMessageBanRequest(
        String reason,
        LocalDateTime expiresAt
) {}
