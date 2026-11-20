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
        request.setCookies(
                new Cookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME, "anon-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        String voterId = manager.getOrCreate(request, response);

        assertThat(voterId).isEqualTo("anon-1");
        assertThat(response.getCookie(AnonymousVoterCookieManager.ANONYMOUS_VOTER_COOKIE_NAME))
                .isNull();
    }

    @Test
    @DisplayName("신뢰 프록시의 전달 체인은 오른쪽부터 확인해 실제 클라이언트 IP를 사용한다")
    void resolve_withForwardedFor_usesRightmostUntrustedIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.10, 127.0.0.1");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("X-Forwarded-For의 첫 항목이 비어 있으면 첫 번째 유효 IP를 사용한다")
    void resolve_withBlankFirstForwardedFor_usesFirstValidIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", ", 203.0.113.10");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("클라이언트가 X-Real-IP를 꾸며 보내도 프록시가 체인 끝에 붙인 주소를 사용한다")
    void resolve_withForgedRealIp_usesProxyAppendedForwardedIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.1");
        request.addHeader("X-Real-IP", "198.51.100.99");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.20");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("Nginx가 덮어쓴 X-Real-IP와 체인 끝 주소가 같으면 그 주소를 사용한다")
    void resolve_withRealIpMatchingForwardedFor_usesThatIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.20");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.20, 203.0.113.20");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("X-Forwarded-For가 여러 줄이면 마지막 줄의 마지막 주소를 사용한다")
    void resolve_withMultipleForwardedForLines_usesLastLine() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        request.addHeader("X-Forwarded-For", "198.51.100.98, 203.0.113.20");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("체인에 사설 주소만 있으면 X-Real-IP를 사용한다")
    void resolve_withOnlyPrivateForwardedFor_fallsBackToRealIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.20");
        request.addHeader("X-Forwarded-For", "10.0.0.5, 127.0.0.1");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("방문자가 IPv6면 체인 끝의 IPv6 주소를 그대로 사용한다")
    void resolve_withIpv6Visitor_usesIpv6() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.99, 2001:db8::1234");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("2001:db8::1234");
    }

    @Test
    @DisplayName("신뢰하지 않는 직접 요청은 전달 헤더를 무시한다")
    void resolve_withUntrustedRemoteAddress_ignoresForwardedHeaders() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.10");
        request.addHeader("X-Real-IP", "203.0.113.20");
        request.addHeader("X-Forwarded-For", "203.0.113.30");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("Docker 사설망의 Nginx가 전달한 실제 클라이언트 IP를 사용한다")
    void resolve_withDockerPrivateProxy_usesRealIp() {
        ClientIpResolver resolver = new ClientIpResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.1");
        request.addHeader("X-Real-IP", "203.0.113.20");

        String ip = resolver.resolve(request);

        assertThat(ip).isEqualTo("203.0.113.20");
    }

    @Test
    @DisplayName("본 글 표시는 최근 50개만 남겨 쿠키가 4KB 한도에 닿지 않는다")
    void markIfFirstView_keepsOnlyRecentPosts() {
        ViewCountCookieManager manager = new ViewCountCookieManager();
        StringBuilder existing = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            existing.append("[").append(java.util.UUID.randomUUID()).append("]");
        }
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME, existing.toString()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        String newest = java.util.UUID.randomUUID().toString();

        boolean first = manager.markIfFirstView(newest, request, response);

        String value =
                response.getCookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME).getValue();
        assertThat(first).isTrue();
        assertThat(value.chars().filter(c -> c == '[').count())
                .isEqualTo(ViewCountCookieManager.MAX_REMEMBERED_POSTS);
        assertThat(value).endsWith("[" + newest + "]").startsWith("[");
        assertThat(value.length()).isLessThan(2_000);
    }

    @Test
    @DisplayName("서버 미리보기가 보내는 [id] 하나짜리 쿠키도 이미 본 글로 처리한다")
    void markIfFirstView_singleMarkerFromPreviewSkipsCount() {
        ViewCountCookieManager manager = new ViewCountCookieManager();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(ViewCountCookieManager.VIEWED_POSTS_COOKIE_NAME, "[abc]"));

        assertThat(manager.markIfFirstView("abc", request, new MockHttpServletResponse()))
                .isFalse();
    }
}
