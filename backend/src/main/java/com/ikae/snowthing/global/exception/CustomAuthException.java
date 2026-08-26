package com.ikae.snowthing.global.exception;

import com.ikae.snowthing.global.error.ErrorCode;

import lombok.Getter;

@Getter
public class CustomAuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomAuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
