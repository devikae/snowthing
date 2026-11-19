package com.ikae.snowthing.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLIntegrityConstraintViolationException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private final ServerErrorAlerter alerter = mock(ServerErrorAlerter.class);
    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(
                    new StaticListableBeanFactory().getBeanProvider(ServerErrorAlerter.class));

    @Test
    @DisplayName("Multipart parser의 업로드 크기 초과를 FILE_SIZE_EXCEEDED 응답으로 변환한다")
    void handleMaxUploadSizeExceededException_returnsFileSizeExceeded() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(5L);

        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSizeExceededException(exception);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED.getStatus());
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.FILE_SIZE_EXCEEDED));
    }

    @Test
    @DisplayName("미처리 예외는 500을 반환하고 장애 알림을 요청한다")
    void handleGeneralException_reportsServerError() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("serverErrorAlerter", alerter);
        GlobalExceptionHandler alertingHandler =
                new GlobalExceptionHandler(beanFactory.getBeanProvider(ServerErrorAlerter.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        IllegalStateException exception = new IllegalStateException("boom");

        ResponseEntity<ErrorResponse> response =
                alertingHandler.handleGeneralException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(alerter).report(request, exception);
    }

    @Test
    @DisplayName("알림 빈이 없어도 미처리 예외는 500을 반환한다")
    void handleGeneralException_withoutAlerter_returnsInternalServerError() {
        ResponseEntity<ErrorResponse> response =
                handler.handleGeneralException(
                        new IllegalStateException("boom"), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("끊기거나 깨진 multipart 요청은 500이 아니라 400으로 답한다")
    void handleMultipartException_returnsInvalidInput() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMultipartException(
                        new MultipartException("Failed to parse multipart servlet request"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("IllegalArgumentException의 내부 문구를 응답에 그대로 내보내지 않는다")
    void handleIllegalArgumentException_hidesInternalMessage() {
        ResponseEntity<ErrorResponse> response =
                handler.handleIllegalArgumentException(
                        new IllegalArgumentException("No enum constant com.ikae.Internal.X"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("무결성 위반 로그에 DB 원문의 입력값(이메일 등)을 남기지 않고 제약 이름만 남긴다")
    void handleDataIntegrityViolation_logsConstraintNameOnly(CapturedOutput output) {
        String email = "leak-check@example.com";
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "could not execute statement [Duplicate entry '" + email + "']",
                        new ConstraintViolationException(
                                "Duplicate entry '" + email + "' for key 'member.uk_member_email'",
                                new SQLIntegrityConstraintViolationException(
                                        "Duplicate entry '" + email + "'"),
                                "uk_member_email"));

        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(output.getAll()).contains("constraint=uk_member_email").doesNotContain(email);
    }
}
