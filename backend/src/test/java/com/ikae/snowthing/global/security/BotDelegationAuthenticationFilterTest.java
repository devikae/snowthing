package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.config.BotProperties;

class BotDelegationAuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void authenticatesAllowedGetAsDelegatedMemberWithoutAdminAuthority() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BotDelegationTokenService tokens =
                new BotDelegationTokenService(properties(), objectMapper);
        MemberRepository members = mock(MemberRepository.class);
        Member member = member(Role.ROLE_ADMIN, MemberStatus.ACTIVE);
        when(members.findByPublicId("member-public-id")).thenReturn(Optional.of(member));
        BotDelegationAuthenticationFilter filter =
                new BotDelegationAuthenticationFilter(tokens, members, objectMapper);
        MockHttpServletRequest request = request("GET", "/api/v1/members/me", tokens);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain =
                (servletRequest, servletResponse) -> {
                    CustomUserDetails principal =
                            (CustomUserDetails)
                                    SecurityContextHolder.getContext()
                                            .getAuthentication()
                                            .getPrincipal();
                    assertThat(principal.getAuthorities())
                            .extracting("authority")
                            .containsExactly("ROLE_USER", "SCOPE_snowthing:read");
                };

        filter.doFilterInternal(request, response, chain);

        verify(members).findByPublicId("member-public-id");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsEveryWriteEvenWithValidToken() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BotDelegationTokenService tokens =
                new BotDelegationTokenService(properties(), objectMapper);
        MemberRepository members = mock(MemberRepository.class);
        BotDelegationAuthenticationFilter filter =
                new BotDelegationAuthenticationFilter(tokens, members, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request("POST", "/api/v1/posts", tokens), response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(members);
    }

    @Test
    void rejectsAdminPathEvenForAdminMemberToken() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BotDelegationTokenService tokens =
                new BotDelegationTokenService(properties(), objectMapper);
        MemberRepository members = mock(MemberRepository.class);
        BotDelegationAuthenticationFilter filter =
                new BotDelegationAuthenticationFilter(tokens, members, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request("GET", "/api/v1/admin/resort-reports", tokens),
                response,
                mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(members);
    }

    @Test
    void rejectsInactiveMember() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        BotDelegationTokenService tokens =
                new BotDelegationTokenService(properties(), objectMapper);
        MemberRepository members = mock(MemberRepository.class);
        when(members.findByPublicId("member-public-id"))
                .thenReturn(Optional.of(member(Role.ROLE_USER, MemberStatus.SUSPENDED)));
        BotDelegationAuthenticationFilter filter =
                new BotDelegationAuthenticationFilter(tokens, members, objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
                request("GET", "/api/v1/members/me", tokens), response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request(
            String method, String path, BotDelegationTokenService tokens) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(
                "Authorization", "Bearer " + tokens.issue("member-public-id", "message-id"));
        return request;
    }

    private Member member(Role role, MemberStatus status) {
        return Member.builder()
                .publicId("member-public-id")
                .email("member@snowthing.org")
                .nickname("라이더")
                .role(role)
                .status(status)
                .build();
    }

    private BotProperties properties() {
        return new BotProperties(
                true,
                URI.create("http://127.0.0.1:3100"),
                SECRET,
                SECRET,
                Duration.ofSeconds(60),
                Duration.ofSeconds(15));
    }
}
