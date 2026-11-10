package com.ikae.snowthing.global.config;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.apitoken.entity.ApiTokenScope;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry;
import com.ikae.snowthing.global.config.websocket.ChatSessionRevocationRegistry;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import com.ikae.snowthing.global.security.ApiTokenAuthenticationFilter;
import com.ikae.snowthing.global.security.BotDelegationAuthenticationFilter;
import com.ikae.snowthing.global.security.BotDelegationTokenService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final String LOGOUT_SUCCESS_MESSAGE = "LOGOUT_SUCCESS";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatSessionRevocationRegistry chatSessionRevocationRegistry;
    private final ChatConnectionRegistry chatConnectionRegistry;
    private final MemberSessionRegistry memberSessionRegistry;

    // 운영 프로필은 localhost를 빼고 https 출처만 둔다.
    @Value(
            "${snowthing.web.allowed-origins:http://localhost:3000,https://snowthing.org,https://www.snowthing.org}")
    private List<String> allowedOrigins;

    // 운영은 https로만 열리므로 CSRF 쿠키에 Secure를 붙인다.
    @Value("${snowthing.web.secure-cookies:false}")
    private boolean secureCookies;

    @Bean
    public ObjectMapper objectMapper() {
        return this.objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secureCookies));
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public BotDelegationAuthenticationFilter botDelegationAuthenticationFilter(
            BotDelegationTokenService tokenService, MemberRepository memberRepository) {
        return new BotDelegationAuthenticationFilter(tokenService, memberRepository, objectMapper);
    }

    @Bean
    public ApiTokenAuthenticationFilter apiTokenAuthenticationFilter(
            ApiTokenService apiTokenService) {
        return new ApiTokenAuthenticationFilter(apiTokenService, objectMapper);
    }

    /** 보안 필터 체인 안에서만 돌게 하고 서블릿 컨테이너에는 따로 등록하지 않는다. */
    @Bean
    public FilterRegistrationBean<ApiTokenAuthenticationFilter> apiTokenFilterRegistration(
            ApiTokenAuthenticationFilter filter) {
        FilterRegistrationBean<ApiTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            BotDelegationAuthenticationFilter botDelegationAuthenticationFilter,
            ApiTokenAuthenticationFilter apiTokenAuthenticationFilter)
            throws Exception {
        http.addFilterBefore(
                        apiTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        botDelegationAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokenRepository())
                                        .ignoringRequestMatchers("/ws-chat/**")
                                        // API 토큰은 쿠키가 아니라 Authorization 헤더로만 온다. 토큰이 틀리면
                                        // ApiTokenAuthenticationFilter가 401로 끝내므로 세션으로 넘어가지 않는다.
                                        // 토큰 허용 경로만 CSRF를 건너뛴다. 로그아웃처럼 토큰 필터보다 앞에서 도는
                                        // 세션 처리에 가짜 Bearer 헤더로 CSRF 없이 들어오지 못하게 한다.
                                        .ignoringRequestMatchers(
                                                request ->
                                                        ApiTokenAuthenticationFilter
                                                                        .isApiTokenRequest(request)
                                                                && ApiTokenAuthenticationFilter
                                                                        .isAllowed(request)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .securityContext(
                        securityContext ->
                                securityContext.securityContextRepository(
                                        securityContextRepository()))
                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                        .sessionFixation(
                                                sessionFixation ->
                                                        sessionFixation.changeSessionId()))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/v1/auth/logout")
                                        .invalidateHttpSession(false)
                                        .clearAuthentication(true)
                                        .deleteCookies("JSESSIONID")
                                        .addLogoutHandler(
                                                (request, response, authentication) -> {
                                                    HttpSession session = request.getSession(false);
                                                    if (session != null) {
                                                        if (authentication != null
                                                                && authentication.getPrincipal()
                                                                        instanceof
                                                                        CustomUserDetails
                                                                                        userDetails) {
                                                            memberSessionRegistry.unregister(
                                                                    userDetails.getPublicId(),
                                                                    session);
                                                        }
                                                        chatConnectionRegistry
                                                                .disconnectHttpSession(
                                                                        session.getId());
                                                        chatSessionRevocationRegistry.revoke(
                                                                session.getId());
                                                        session.invalidate();
                                                    }
                                                })
                                        .logoutSuccessHandler(
                                                (request, response, authentication) -> {
                                                    response.setStatus(HttpServletResponse.SC_OK);
                                                    response.setContentType(JSON_CONTENT_TYPE);
                                                    objectMapper.writeValue(
                                                            response.getWriter(),
                                                            Map.of(
                                                                    "message",
                                                                    LOGOUT_SUCCESS_MESSAGE));
                                                }))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (request, response, authException) -> {
                                                    response.setStatus(
                                                            HttpServletResponse.SC_UNAUTHORIZED);
                                                    response.setContentType(JSON_CONTENT_TYPE);
                                                    objectMapper.writeValue(
                                                            response.getWriter(),
                                                            ErrorResponse.from(
                                                                    ErrorCode.LOGIN_REQUIRED));
                                                })
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) -> {
                                                    response.setStatus(
                                                            HttpServletResponse.SC_FORBIDDEN);
                                                    response.setContentType(JSON_CONTENT_TYPE);
                                                    objectMapper.writeValue(
                                                            response.getWriter(),
                                                            ErrorResponse.from(
                                                                    ErrorCode.ACCESS_DENIED));
                                                }))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/market-listings/preview",
                                                "/api/v1/market-listings/*/share-preview",
                                                "/api/v1/carpools/**",
                                                "/api/carpools/**",
                                                "/api/v1/resort-reports/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/carpools/auto-preview",
                                                "/api/carpools/auto-preview")
                                        .permitAll()
                                        // IKA-27 비회원 캐릭터 테스트. 메서드와 경로를 정확히 적고 CSRF 검사는 그대로 받는다.
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/character-test/result",
                                                "/api/v1/character-groups",
                                                "/api/v1/character-groups/*/participants")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/character-groups/*",
                                                "/api/v1/character-groups/*/map")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.DELETE,
                                                "/api/v1/character-participants/me")
                                        .permitAll()
                                        // IKA-64 공유를 켠 시즌 결산만 공개한다. 내 기록 API는 아래 인증 규칙을 따른다.
                                        .requestMatchers(HttpMethod.GET, "/api/v1/season-shares/*")
                                        .permitAll()
                                        // 소셜 계정 연결은 로그인한 회원만 시작한다. 아래 oauth2 전체 허용보다 먼저 둔다.
                                        .requestMatchers("/api/v1/auth/oauth2/link/**")
                                        .authenticated()
                                        .requestMatchers(
                                                "/api/members",
                                                "/api/v1/members",
                                                "/api/auth/login",
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/oauth2/**",
                                                "/api/v1/auth/email-verifications/**",
                                                "/api/v1/auth/password-reset",
                                                "/api/v1/auth/password-reset/**",
                                                "/api/v1/members/email-availability",
                                                "/api/auth/signup",
                                                "/api/v1/auth/signup",
                                                "/api/v1/csrf",
                                                "/api/resorts",
                                                "/api/v1/master/resorts",
                                                "/api/riding-styles",
                                                "/api/v1/master/riding-styles",
                                                "/api/v1/resort-cams",
                                                "/api/v1/resort-cams/*/weather",
                                                "/api/v1/slope-status",
                                                "/api/posts",
                                                "/api/posts/**",
                                                "/api/v1/posts",
                                                "/api/v1/posts/**",
                                                "/api/comments",
                                                "/api/comments/**",
                                                "/api/v1/comments",
                                                "/api/v1/comments/**",
                                                "/ws-chat",
                                                "/ws-chat/**",
                                                "/api/v1/chat/**",
                                                "/api/v1/public/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/admin/search/evidence",
                                                "/api/v1/admin/search/evidence/*")
                                        .hasAnyAuthority(
                                                Role.ROLE_ADMIN.getKey(),
                                                ApiTokenScope.READ.getAuthority())
                                        .requestMatchers(HttpMethod.POST, "/api/v1/answer-drafts")
                                        .hasAnyAuthority(
                                                Role.ROLE_ADMIN.getKey(),
                                                ApiTokenScope.DRAFT.getAuthority())
                                        .requestMatchers(HttpMethod.GET, "/api/v1/answer-drafts/*")
                                        .hasAnyAuthority(
                                                Role.ROLE_ADMIN.getKey(),
                                                ApiTokenScope.DRAFT.getAuthority())
                                        .requestMatchers("/api/v1/answer-drafts/**")
                                        .denyAll()
                                        .requestMatchers("/api/admin/**", "/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated());

        return http.build();
    }
}
