package com.ikae.snowthing.global.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

class ServerErrorAlerterTest {

    private final DiscordNotifier notifier = mock(DiscordNotifier.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-04T05:03:12Z"), ZoneOffset.UTC);
    private final ServerErrorAlerter alerter =
            new ServerErrorAlerter(notifier, new ErrorAlertThrottle(clock), clock);

    @Test
    @DisplayName("KST 시각, 쿼리 없는 경로, 예외와 위치만 담아 보낸다")
    void formatsWithoutQueryOrBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/posts");
        request.setQueryString("email=rider@example.com");
        request.setContent("{\"password\":\"secret\"}".getBytes(StandardCharsets.UTF_8));

        alerter.report(request, new IllegalStateException("상태 오류\n다음 줄"));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifier).alert(message.capture());
        assertThat(message.getValue())
                .contains("🚨 500 에러")
                .contains("시각: 2026-11-04 14:03:12 KST")
                .contains("요청: POST /api/v1/posts\n")
                .contains("예외: java.lang.IllegalStateException: 상태 오류 다음 줄")
                .contains("위치: ServerErrorAlerterTest.formatsWithoutQueryOrBody(")
                .doesNotContain("rider@example.com")
                .doesNotContain("secret")
                .doesNotContain("지난 알림");
    }

    @Test
    @DisplayName("같은 위치의 같은 예외는 5분 안에 한 번만 보낸다")
    void throttlesRepeatedErrors() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        for (int i = 0; i < 3; i++) {
            alerter.report(request, failAtSameLine());
        }

        verify(notifier, times(1)).alert(anyString());
    }

    @Test
    @DisplayName("바깥 예외에 프로젝트 프레임이 없으면 원인 예외에서 찾는다")
    void locatesProjectFrameInCause() {
        RuntimeException cause = new RuntimeException("원인");
        RuntimeException wrapper = new RuntimeException("포장", cause);
        wrapper.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("org.example.Lib", "call", "Lib.java", 10)
                });

        assertThat(ServerErrorAlerter.locate(wrapper))
                .startsWith("ServerErrorAlerterTest.locatesProjectFrameInCause(");
    }

    @Test
    @DisplayName("알림 준비 중 예외가 나도 밖으로 던지지 않는다")
    void neverThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getMethod()).willThrow(new IllegalStateException("broken"));

        assertThatCode(() -> alerter.report(request, new RuntimeException()))
                .doesNotThrowAnyException();
        verify(notifier, never()).alert(anyString());
    }

    private static RuntimeException failAtSameLine() {
        return new IllegalArgumentException("같은 줄");
    }
}
