package com.ikae.snowthing.domain.message.dto;

import java.time.LocalDateTime;

public record DirectMessageSendResponse(String id, LocalDateTime createdAt) {}
