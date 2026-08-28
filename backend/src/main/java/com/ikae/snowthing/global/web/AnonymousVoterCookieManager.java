package com.ikae.snowthing.global.web;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

@Component
public class AnonymousVoterCookieManager {

    public static final String ANONYMOUS_VOTER_COOKIE_NAME = "anonymous_voter_id";
    private static final int ANONYMOUS_VOTER_COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;
    private static final String COOKIE_PATH = "/";

    public String getOrCreate(HttpServletRequest request, HttpServletResponse response) {
        Cookie cookie = findCookie(request, ANONYMOUS_VOTER_COOKIE_NAME);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }

        String voterId = UUID.randomUUID().toString();
        Cookie newCookie = new Cookie(ANONYMOUS_VOTER_COOKIE_NAME, voterId);
        newCookie.setPath(COOKIE_PATH);
        newCookie.setMaxAge(ANONYMOUS_VOTER_COOKIE_MAX_AGE_SECONDS);
        newCookie.setHttpOnly(true);
        response.addCookie(newCookie);
        return voterId;
    }

    private Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }
}
