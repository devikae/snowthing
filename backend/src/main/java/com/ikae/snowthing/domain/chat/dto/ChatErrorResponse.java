package com.ikae.snowthing.domain.chat.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.ikae.snowthing.global.error.ErrorCode;

public record ChatErrorResponse(String code, String message, String timestamp) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static ChatErrorResponse retry(ErrorCode errorCode, long retryAfterSeconds) {
        return new ChatErrorResponse(
                errorCode.getCode(),
                errorCode.getMessage() + " 약 " + retryAfterSeconds + "초 후 다시 시도해 주세요.",
                LocalDateTime.now().format(FORMATTER));
    }

    public static ChatErrorResponse from(ErrorCode errorCode) {
        return new ChatErrorResponse(
                errorCode.getCode(), errorCode.getMessage(), LocalDateTime.now().format(FORMATTER));
    }
}
