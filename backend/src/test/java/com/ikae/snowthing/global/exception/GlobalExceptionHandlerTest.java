package com.ikae.snowthing.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Multipart parser의 업로드 크기 초과를 FILE_SIZE_EXCEEDED 응답으로 변환한다")
    void handleMaxUploadSizeExceededException_returnsFileSizeExceeded() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(5L);

        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSizeExceededException(exception);

        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED.getStatus());
        assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.FILE_SIZE_EXCEEDED));
    }
}
