package com.ikae.snowthing.domain.chat.dto;

public record ChatMessageResponse(
        String messageId, SenderDto sender, String resortTag, String content, String sentAt) {}
