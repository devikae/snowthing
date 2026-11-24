package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;

/**
 * 관리자 API 권한 회귀 표 (IKA-118).
 *
 * <p>등록된 컨트롤러 경로를 읽어 {@code /api/v1/admin/**}, {@code /api/admin/**}에 해당하는 모든 엔드포인트가 비로그인에게 401, 일반
 * 회원에게 403을 주는지 훑는다. 표를 손으로 적지 않으므로 관리자 API를 새로 만들면 자동으로 검사 대상이 된다. 권한 검사는 컨트롤러보다 앞에서 끝나야 하므로 요청
 * 본문은 빈 JSON이고 DB를 쓰지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminApiAccessRegressionTest {

    /** 관리자 API가 한꺼번에 사라지는 설정 실수를 잡기 위한 하한. 2026-11-24 기준 실제 수보다 넉넉히 작다. */
    private static final int MINIMUM_ADMIN_ENDPOINTS = 30;

    @Autowired private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("모든 관리자 API는 비로그인 401, 일반 회원 403이다")
    void everyAdminEndpointRejectsGuestsAndMembers() throws Exception {
        List<Endpoint> endpoints = adminEndpoints();
        assertThat(endpoints).hasSizeGreaterThanOrEqualTo(MINIMUM_ADMIN_ENDPOINTS);
        CustomUserDetails member =
                new CustomUserDetails(
                        Member.builder()
                                .email("regression-member@example.com")
                                .nickname("일반회원")
                                .role(Role.ROLE_USER)
                                .build());

        SoftAssertions softly = new SoftAssertions();
        for (Endpoint endpoint : endpoints) {
            softly.assertThat(
                            mockMvc.perform(endpoint.request())
                                    .andReturn()
                                    .getResponse()
                                    .getStatus())
                    .as("비로그인 " + endpoint)
                    .isEqualTo(401);
            softly.assertThat(
                            mockMvc.perform(endpoint.request().with(user(member)))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus())
                    .as("일반 회원 " + endpoint)
                    .isEqualTo(403);
        }
        softly.assertAll();
    }

    @Test
    @DisplayName("관리자 API의 쓰기 요청은 CSRF 토큰이 없으면 관리자 세션이어도 403이다")
    void adminWritesRequireCsrf() throws Exception {
        CustomUserDetails admin =
                new CustomUserDetails(
                        Member.builder()
                                .email("regression-admin@example.com")
                                .nickname("관리자")
                                .role(Role.ROLE_ADMIN)
                                .build());

        SoftAssertions softly = new SoftAssertions();
        for (Endpoint endpoint : adminEndpoints()) {
            if (endpoint.method() == HttpMethod.GET) {
                continue;
            }
            softly.assertThat(
                            mockMvc.perform(
                                            request(endpoint.method(), URI.create(endpoint.path()))
                                                    .with(user(admin))
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content("{}"))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus())
                    .as("CSRF 없음 " + endpoint)
                    .isEqualTo(403);
        }
        softly.assertAll();
    }

    private List<Endpoint> adminEndpoints() {
        Set<String> seen = new TreeSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                if (!pattern.startsWith("/api/v1/admin/") && !pattern.startsWith("/api/admin/")) {
                    continue;
                }
                // 경로 변수 자리는 아무 값이나 넣는다. 권한 검사는 값을 보기 전에 끝난다.
                String path = pattern.replaceAll("\\{[^/]+}", "1");
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    if (seen.add(method + " " + path)) {
                        endpoints.add(new Endpoint(HttpMethod.valueOf(method.name()), path));
                    }
                }
            }
        }
        return endpoints;
    }

    private record Endpoint(HttpMethod method, String path) {

        MockHttpServletRequestBuilder request() {
            return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                            method, URI.create(path))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}");
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }
}
