package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * API 토큰이 갈 수 있는 경로 표 (IKA-118). 이 표가 CSRF 예외 범위이기도 하므로 넓어지면 안 된다.
 *
 * <p>허용: 근거 검색과 근거 한 건 조회(GET), 초안 저장(POST), 초안 상태 조회(GET). 나머지는 모두 거부다.
 */
class ApiTokenAuthenticationFilterTest {

    private static final String DRAFT_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    @DisplayName("허용 경로 네 가지만 통과한다")
    void allowsOnlyTheFourRoutes() {
        assertThat(allowed("GET", "/api/v1/admin/search/evidence")).isTrue();
        assertThat(allowed("GET", "/api/v1/admin/search/evidence/C:12:0")).isTrue();
        assertThat(allowed("POST", "/api/v1/answer-drafts")).isTrue();
        assertThat(allowed("GET", "/api/v1/answer-drafts/" + DRAFT_ID)).isTrue();
    }

    @Test
    @DisplayName("같은 경로라도 메서드가 다르면 거부한다")
    void rejectsOtherMethods() {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            assertThat(allowed(method, "/api/v1/admin/search/evidence")).as(method).isFalse();
            assertThat(allowed(method, "/api/v1/admin/search/evidence/C:12:0"))
                    .as(method)
                    .isFalse();
            assertThat(allowed(method, "/api/v1/answer-drafts/" + DRAFT_ID)).as(method).isFalse();
        }
        for (String method : List.of("GET", "PUT", "PATCH", "DELETE")) {
            assertThat(allowed(method, "/api/v1/answer-drafts")).as(method).isFalse();
        }
    }

    @Test
    @DisplayName("끝 슬래시, 하위 경로, 경로 매개변수, 상위 이동, 다른 접두사는 거부한다")
    void rejectsLookalikePaths() {
        for (String path :
                List.of(
                        "/api/v1/answer-drafts/",
                        "/api/v1/answer-drafts/" + DRAFT_ID + "/",
                        "/api/v1/answer-drafts/" + DRAFT_ID + "/publish",
                        "/api/v1/answer-drafts/" + DRAFT_ID + ";jsessionid=abc",
                        "/api/v1/answer-drafts/not-a-uuid",
                        "/api/v1/answer-drafts/../admin/answer-drafts",
                        "/api/v1/answer-drafts/%2e%2e/admin/answer-drafts",
                        "/api/v1/admin/answer-drafts",
                        "/api/v1/admin/answer-drafts/" + DRAFT_ID,
                        "/api/v1/admin/search/evidence/",
                        "/api/v1/admin/search/evidence/C:12:0/extra",
                        "/api/v1/admin/search",
                        "/api/v1/admin/api-tokens",
                        "/api/answer-drafts",
                        "/API/V1/ANSWER-DRAFTS/" + DRAFT_ID,
                        "/api/v1/notifications",
                        "/api/v1/auth/logout")) {
            assertThat(allowed("GET", path)).as("GET " + path).isFalse();
            assertThat(allowed("POST", path)).as("POST " + path).isFalse();
        }
    }

    @Test
    @DisplayName("Authorization 헤더가 정확히 'Bearer snt_'로 시작할 때만 토큰 요청으로 본다")
    void recognizesOnlyBearerSntHeader() {
        assertThat(tokenRequest("Bearer snt_" + "A".repeat(43))).isTrue();
        assertThat(tokenRequest("Bearer snt_")).isTrue();

        assertThat(tokenRequest(null)).isFalse();
        assertThat(tokenRequest("")).isFalse();
        assertThat(tokenRequest("snt_" + "A".repeat(43))).isFalse();
        assertThat(tokenRequest("Basic snt_abc")).isFalse();
        assertThat(tokenRequest("Bearer eyJhbGciOiJIUzI1NiJ9.e30.x")).isFalse();
        assertThat(tokenRequest("bearer snt_abc")).isFalse();
        assertThat(tokenRequest("Bearer  snt_abc")).isFalse();
    }

    private static boolean allowed(String method, String path) {
        return ApiTokenAuthenticationFilter.isAllowed(new MockHttpServletRequest(method, path));
    }

    private static boolean tokenRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        return ApiTokenAuthenticationFilter.isApiTokenRequest(request);
    }
}
