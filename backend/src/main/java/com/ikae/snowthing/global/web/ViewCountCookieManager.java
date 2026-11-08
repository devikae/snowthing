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
    // 글 ID는 36자라 표시 하나가 38자다. 끝없이 붙이면 쿠키 4KB 한도에 닿아 브라우저가 버리고, 그때부터 새로고침마다 조회수가 오른다.
    // 최근 본 글만 남겨 2KB 안쪽으로 묶는다.
    static final int MAX_REMEMBERED_POSTS = 50;

    public boolean markIfFirstView(
            String postPublicId, HttpServletRequest request, HttpServletResponse response) {
        Cookie viewCookie = findCookie(request, VIEWED_POSTS_COOKIE_NAME);
        String marker = "[" + postPublicId + "]";

        if (viewCookie != null && viewCookie.getValue().contains(marker)) {
            return false;
        }

        String value = keepRecent((viewCookie == null ? "" : viewCookie.getValue()) + marker);
        Cookie cookie = new Cookie(VIEWED_POSTS_COOKIE_NAME, value);
        cookie.setPath(COOKIE_PATH);
        cookie.setMaxAge(VIEW_COOKIE_MAX_AGE_SECONDS);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return true;
    }

    /** "[id][id]..." 형식에서 뒤쪽(최근) 표시만 남긴다. 형식이 깨진 앞부분은 버린다. */
    static String keepRecent(String value) {
        int count = 0;
        for (int index = value.length() - 1; index >= 0; index--) {
            if (value.charAt(index) == '[' && ++count == MAX_REMEMBERED_POSTS) {
                return value.substring(index);
            }
        }
        int first = value.indexOf('[');
        return first <= 0 ? value : value.substring(first);
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
