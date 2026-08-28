package com.ikae.snowthing.global.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

@Component
public class ViewCountCookieManager {

    public static final String VIEWED_POSTS_COOKIE_NAME = "viewed_posts";
    private static final int VIEW_COOKIE_MAX_AGE_SECONDS = 30 * 60;
    private static final String COOKIE_PATH = "/";

    public boolean markIfFirstView(
            String postPublicId, HttpServletRequest request, HttpServletResponse response) {
        Cookie viewCookie = findCookie(request, VIEWED_POSTS_COOKIE_NAME);
        String marker = "[" + postPublicId + "]";

        if (viewCookie != null && viewCookie.getValue().contains(marker)) {
            return false;
        }

        String value = viewCookie == null ? marker : viewCookie.getValue() + marker;
        Cookie cookie = new Cookie(VIEWED_POSTS_COOKIE_NAME, value);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(VIEW_COOKIE_MAX_AGE_SECONDS);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return true;
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
