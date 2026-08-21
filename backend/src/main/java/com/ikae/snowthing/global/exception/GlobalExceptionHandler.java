package com.ikae.snowthing.global.exception;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ErrorResponse> handleCustomAuthException(CustomAuthException e) {
        ErrorCode errorCode = e.getErrorCode() != null ? e.getErrorCode() : ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.from(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        if ("INVALID_CREDENTIALS".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.from(ErrorCode.INVALID_CREDENTIALS));
        }
        if ("DUPLICATE_EMAIL".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(ErrorCode.DUPLICATE_EMAIL));
        }
        if ("DUPLICATE_NICKNAME".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(ErrorCode.DUPLICATE_NICKNAME));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String defaultMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, defaultMessage != null ? defaultMessage : ErrorCode.INVALID_INPUT.getMessage()));
    }
}
