package com.ikae.snowthing.global.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AccountStatusException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraft;
import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 웹 슬라이스 테스트처럼 알림 빈이 없는 컨텍스트에서도 핸들러가 뜨도록 선택 주입한다. */
    private final ObjectProvider<ServerErrorAlerter> serverErrorAlerter;

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode =
                e.getErrorCode() != null ? e.getErrorCode() : ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ErrorResponse.from(e.getErrorCode()));
    }

    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ErrorResponse> handleCustomAuthException(CustomAuthException e) {
        ErrorCode errorCode =
                e.getErrorCode() != null ? e.getErrorCode() : ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약조건 위반 발생: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, "요청 데이터의 고유 제약조건 또는 무결성을 위반했습니다."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException e) {
        ErrorCode errorCode =
                MarketListing.class.equals(e.getPersistentClass())
                        ? ErrorCode.MARKET_LISTING_UPDATE_CONFLICT
                        : AnswerDraft.class.equals(e.getPersistentClass())
                                ? ErrorCode.ANSWER_DRAFT_UPDATE_CONFLICT
                                : ErrorCode.COMMENT_UPDATE_CONFLICT;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // 정지, 탈퇴 계정(AccountStatusException)도 같은 401로 답한다. 비밀번호 검사 전에 나는 예외라 다른 문구를 주면
    // 비밀번호를 몰라도 가입 여부와 계정 상태를 알 수 있다.
    @ExceptionHandler({
        BadCredentialsException.class,
        UsernameNotFoundException.class,
        AccountStatusException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.from(ErrorCode.INVALID_CREDENTIALS));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException e) {
        // 라이브러리 내부 문구가 사용자에게 그대로 나가지 않게 고정 문구로 답하고 원문은 로그에만 남긴다.
        log.debug("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    // 모바일에서 사진 업로드가 끊기거나 multipart 본문이 깨지면 난다. 사용자 쪽 문제라 500 알림으로 보내지 않는다.
    // 크기 초과(MaxUploadSizeExceededException)는 더 구체적인 처리기가 따로 받는다.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException e) {
        log.debug("Malformed multipart request: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    // 응답을 쓰는 중에 클라이언트가 연결을 끊었다. 보낼 곳이 없으므로 알림 없이 끝낸다.
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(AsyncRequestNotUsableException e) {
        log.debug("Client disconnected: {}", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e) {
        String defaultMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorResponse.of(
                                ErrorCode.INVALID_INPUT,
                                defaultMessage != null
                                        ? defaultMessage
                                        : ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽을 수 없습니다: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e) {
        log.warn("Multipart upload size exceeded: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.FILE_SIZE_EXCEEDED.getStatus())
                .body(ErrorResponse.from(ErrorCode.FILE_SIZE_EXCEEDED));
    }

    /** 없는 주소를 500으로 돌려주면 스캐너 요청마다 서버 오류 알림이 간다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ErrorResponse.from(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ErrorResponse.from(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler({
        HttpMediaTypeNotSupportedException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception e, HttpServletRequest request) {
        log.error("서버 내부 미처리 예외 발생: ", e);
        serverErrorAlerter.ifAvailable(alerter -> alerter.report(request, e));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.from(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
