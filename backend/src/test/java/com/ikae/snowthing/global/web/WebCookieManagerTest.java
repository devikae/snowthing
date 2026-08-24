package com.ikae.snowthing.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebCookieManagerTest {

    @Test
    @DisplayName("조회수 쿠키가 없으면 최초 조회로 판단하고 viewed_posts 쿠키를 발급한다")
    void markIfFirstView_withoutCookie_returnsTrueAndAddsCookie() {
        ViewCountCookieManager manager = new ViewCountCookieManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean firstView = manager.markIfFirstView("post-1", request, response);

        assertThat(firstView).isTrue();
        Cookie cookie = response.getCookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).contains("[post-1]");
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("조회수 쿠키에 같은 게시글 표시가 있으면 중복 조회로 판단한다")
    void markIfFirstView_withExistingMarker_returnsFalse() {
        ViewCountCookieManager manager = new ViewCountCookieManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME, "[post-1]"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean firstView = manager.markIfFirstView("post-1", request, response);

        assertThat(firstView).isFalse();
        assertThat(response.getCookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME)).isNull();
    }

    @Test
    @DisplayName("익명 투표자 쿠키가 없으면 새 anonymous_voter_id 쿠키를 발급한다")
    void getOrCreate_withoutCookie_createsAnonymousVoterCookie() {
        AnonymousVoterCookieManager manager = new AnonymousVoterCookieManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String voterId = manager.getOrCreate(request, response);

        assertThat(voterId).isNotBlank();
        Cookie cookie = response.getCookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(voterId);
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    @Test
    @DisplayName("익명 투표자 쿠키가 있으면 기존 anonymous_voter_id 값을 재사용한다")
    void getOrCreate_withCookie_reusesAnonymousVoterCookie() {
        AnonymousVoterCookieManager manager = new AnonymousVoterCookieManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME, "anon-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String voterId = manager.getOrCreate(request, response);

        assertThat(voterId).isEqualTo("anon-1");
        assertThat(response.getCookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME)).isNull();
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더가 여러 IP를 가지면 첫 번째 IP를 클라이언트 IP로 사용한다")
    void resolve_withForwardedFor_usesFirstIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.10");
    }
}
