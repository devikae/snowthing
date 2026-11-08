package com.ikae.snowthing.global.notification;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.OptionalLong;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 요청 처리 중 미처리 예외(500)를 #장애 채널로 알린다.
 *
 * <p>요청 본문, 쿼리 문자열, 헤더, 사용자 정보는 넣지 않는다. 경로는 쿼리를 포함하지 않는 getRequestURI만 쓴다.
 */
@Slf4j
@Component
public class ServerErrorAlerter {

    static final String PROJECT_PACKAGE = "com.ikae.snowthing";
    static final int MAX_MESSAGE_LENGTH = 200;
    static final int MAX_CAUSE_DEPTH = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DiscordNotifier notifier;
    private final ErrorAlertThrottle throttle;
    private final Clock clock;

    @Autowired
    public ServerErrorAlerter(DiscordNotifier notifier, ErrorAlertThrottle throttle) {
        this(notifier, throttle, Clock.systemUTC());
    }

    ServerErrorAlerter(DiscordNotifier notifier, ErrorAlertThrottle throttle, Clock clock) {
        this.notifier = notifier;
        this.throttle = throttle;
        this.clock = clock;
    }

    /** 알림 처리 중 어떤 예외도 밖으로 내보내지 않는다. 500 응답은 그대로 나가야 한다. */
    public void report(HttpServletRequest request, Throwable error) {
        try {
            String location = locate(error);
            OptionalLong suppressed =
                    throttle.tryAcquire(error.getClass().getName() + "@" + location);
            if (suppressed.isPresent()) {
                notifier.alert(format(request, error, location, suppressed.getAsLong()));
            }
        } catch (RuntimeException e) {
            log.warn("500 알림 준비 실패: {}", e.getClass().getSimpleName());
        }
    }

    String format(HttpServletRequest request, Throwable error, String location, long suppressed) {
        StringBuilder message =
                new StringBuilder("🚨 500 에러\n")
                        .append("시각: ")
                        .append(ZonedDateTime.now(clock).withZoneSameInstant(KST).format(TIME))
                        .append(" KST\n")
                        .append("요청: ")
                        .append(request.getMethod())
                        .append(' ')
                        .append(request.getRequestURI())
                        .append('\n')
                        .append("예외: ")
                        .append(error.getClass().getName());
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            message.append(": ").append(shorten(error.getMessage()));
        }
        message.append('\n').append("위치: ").append(location);
        if (suppressed > 0) {
            message.append('\n').append("(지난 알림 이후 ").append(suppressed).append("회)");
        }
        return message.toString();
    }

    /** 원인 예외까지 따라가며 첫 프로젝트 스택 프레임을 찾는다. 없으면 맨 위 프레임을 쓴다. */
    static String locate(Throwable error) {
        StackTraceElement fallback = null;
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            for (StackTraceElement frame : current.getStackTrace()) {
                if (fallback == null) {
                    fallback = frame;
                }
                if (frame.getClassName().startsWith(PROJECT_PACKAGE)) {
                    return describe(frame);
                }
            }
            current = current.getCause();
        }
        return fallback != null ? describe(fallback) : "unknown";
    }

    private static String describe(StackTraceElement frame) {
        String className = frame.getClassName();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName
                + "."
                + frame.getMethodName()
                + "("
                + frame.getFileName()
                + ":"
                + frame.getLineNumber()
                + ")";
    }

    private static String shorten(String text) {
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= MAX_MESSAGE_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }
}
