package com.ikae.snowthing.domain.chat.bot;

public record BotAnswerRequest(
        String requestId, String question, String resortTag, String delegationToken) {}
