package com.ikae.snowthing.global.security;

import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

/**
 * 이 세션에서 마지막으로 로그인한 시각을 세션에 남긴다. 세션 생성 시각은 로그인 전 소셜 인증 state를 담을 때 이미 정해지고 로그인 때 세션 id만 바뀌므로 로그인
 * 시각으로 쓸 수 없다.
 */
public final class RecentAuthentication {

    static final String SESSION_ATTRIBUTE = "SNOWTHING_AUTHENTICATED_AT";

    private RecentAuthentication() {}

    public static void mark(HttpSession session, Instant now) {
        session.setAttribute(SESSION_ATTRIBUTE, now.toEpochMilli());
    }

    /** 기록이 없는 세션(이 기능 배포 전에 로그인한 세션 포함)은 오래된 로그인으로 본다. */
    public static boolean isWithin(HttpSession session, Instant now, Duration maxAge) {
        if (session == null) {
            return false;
        }
        Object value;
        try {
            value = session.getAttribute(SESSION_ATTRIBUTE);
        } catch (IllegalStateException invalidated) {
            return false;
        }
        if (!(value instanceof Long authenticatedAt)) {
            return false;
        }
        long age = now.toEpochMilli() - authenticatedAt;
        return age >= 0 && age <= maxAge.toMillis();
    }
}
