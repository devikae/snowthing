package com.ikae.snowthing.global.error;

public record ErrorResponse(
        String code,
        String error,
        String message
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return new ErrorResponse(errorCode.getCode(), errorCode.name(), customMessage);
    }
}
