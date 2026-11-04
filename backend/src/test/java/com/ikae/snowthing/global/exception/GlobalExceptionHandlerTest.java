package com.ikae.snowthing.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

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
}
