package com.ikae.snowthing.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 상태를 30일 유지하는 쿠키를 다룬다. 세션은 서버 메모리에 있어 60분 쉬거나 배포하면 사라진다. 세션이 없을 때 이 쿠키로 다시 인증해 새 세션을 만든다.
 *
 * <p>Spring Security의 PersistentTokenBasedRememberMeServices와 같은 series와 token 방식이다. series는 기기 하나를
 * 가리키고 token은 자동 로그인 때마다 바뀐다. 오래된 token이 들어오면 쿠키를 훔쳐 쓴 것으로 보고 그 회원의 토큰을 모두 지운다. 기본 구현을 그대로 쓰지 않은
 * 이유는 세 가지다.
 *
 * <ul>
 *   <li>프론트는 화면 하나에서 API를 여러 개 동시에 부른다. 기본 구현은 첫 요청이 token을 바꾸면 나머지 요청을 도난으로 보고 로그아웃시킨다. 그래서 바로 전
 *       token은 바꾼 뒤 잠깐 동안 받아 준다.
 *   <li>token 원문 대신 해시를 저장한다.
 *   <li>회원을 이메일이 아니라 공개 ID로 묶는다.
 * </ul>
 */
@Slf4j
@Component
public class SnowthingRememberMeServices extends AbstractRememberMeServices {

    public static final String COOKIE_NAME = "SNOWTHING_REMEMBER_ME";
    public static final Duration TOKEN_VALIDITY = Duration.ofDays(30);

    /** 이 요청이 로그인 유지 쿠키로 인증됐다는 표시. 세션 등록 필터가 읽는다. */
    static final String AUTO_LOGIN_ATTRIBUTE =
            SnowthingRememberMeServices.class.getName() + ".AUTO_LOGIN";

    /** token을 바꾼 뒤 바로 전 token을 받아 주는 시간. 동시에 출발한 요청이 모두 도착할 만큼만 둔다. */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(60);

    private static final String KEY_DOMAIN = "snowthing-remember-me-v1:";
    private static final int RANDOM_BYTES = 32;

    private final RememberMeTokenRepository tokenRepository;
    private final MemberSessionRegistry memberSessionRegistry;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SnowthingRememberMeServices(
            @Value("${snowthing.remember-me.key:}") String key,
            @Value("${snowthing.email.verification.secret}") String fallbackSecret,
            @Value("${snowthing.web.secure-cookies:false}") boolean secureCookies,
            MemberRepository memberRepository,
            RememberMeTokenRepository tokenRepository,
            MemberSessionRegistry memberSessionRegistry,
            Clock clock) {
        super(
                resolveKey(key, fallbackSecret),
                publicId ->
                        memberRepository
                                .findByPublicId(publicId)
                                .map(CustomUserDetails::new)
                                .orElseThrow(
                                        () ->
                                                new UsernameNotFoundException(
                                                        ErrorCode.MEMBER_NOT_FOUND.getMessage())));
        this.tokenRepository = tokenRepository;
        this.memberSessionRegistry = memberSessionRegistry;
        this.clock = clock;
        setCookieName(COOKIE_NAME);
        // 로그인 화면에 체크박스가 없어 로그인하면 항상 유지한다.
        setAlwaysRemember(true);
        setTokenValiditySeconds((int) TOKEN_VALIDITY.toSeconds());
        setUseSecureCookie(secureCookies);
        setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
    }

    /**
     * 이 키는 쿠키를 서명하지 않는다. 자동 로그인으로 만든 인증 객체가 이 서버에서 나왔는지 확인하는 데만 쓴다. 따로 정하지 않으면 운영에 이미 있는 비밀값에 용도
     * 접두사를 붙여 쓴다.
     */
    private static String resolveKey(String key, String fallbackSecret) {
        if (key != null && !key.isBlank()) {
            return key;
        }
        return KEY_DOMAIN + fallbackSecret;
    }

    @Override
    protected void onLoginSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication successfulAuthentication) {
        if (!(successfulAuthentication.getPrincipal() instanceof CustomUserDetails user)) {
            return;
        }
        // 관리자는 로그인 유지를 하지 않는다. 쿠키가 털렸을 때 피해가 가장 크다.
        if (isAdmin(user)) {
            deletePresentedSeries(request);
            cancelCookie(request, response);
            return;
        }
        String series = randomValue();
        String token = randomValue();
        try {
            // 같은 브라우저에 남은 이전 토큰은 더 쓰지 않으니 지운다.
            deletePresentedSeries(request);
            tokenRepository.create(
                    series, user.getPublicId(), hash(token), LocalDateTime.now(clock));
        } catch (DataAccessException exception) {
            // 로그인 유지를 못 해도 이번 로그인은 성공시킨다. 세션이 끝나면 다시 로그인하게 된다.
            log.error("로그인 유지 토큰 저장 실패 member={}", user.getPublicId(), exception);
            return;
        }
        setCookie(new String[] {series, token}, getTokenValiditySeconds(), request, response);
    }

    @Override
    protected UserDetails processAutoLoginCookie(
            String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new InvalidCookieException("로그인 유지 쿠키 형식이 아닙니다.");
        }
        String series = cookieTokens[0];
        String presentedHash = hash(cookieTokens[1]);
        RememberMeTokenRepository.StoredToken stored =
                tokenRepository
                        .findBySeries(series)
                        .orElseThrow(
                                () -> new RememberMeAuthenticationException("없는 로그인 유지 토큰입니다."));
        LocalDateTime now = LocalDateTime.now(clock);
        if (stored.lastUsedAt().plus(TOKEN_VALIDITY).isBefore(now)) {
            tokenRepository.deleteSeries(series);
            throw new RememberMeAuthenticationException("로그인 유지 기간이 지났습니다.");
        }

        if (sameHash(presentedHash, stored.tokenHash())) {
            String newToken = randomValue();
            if (tokenRepository.rotate(series, stored.tokenHash(), hash(newToken), now)) {
                setCookie(
                        new String[] {series, newToken},
                        getTokenValiditySeconds(),
                        request,
                        response);
            }
            // 바꾸지 못했으면 같이 출발한 다른 요청이 먼저 바꿨다. 새 쿠키는 그 요청의 응답이 내려준다.
        } else if (!isPreviousTokenInGrace(stored, presentedHash, now)) {
            // 토큰만 지우면 훔친 쿠키로 이미 만든 세션이 60분 동안 살아 있다. 세션도 함께 끊는다.
            memberSessionRegistry.invalidateAll(stored.memberPublicId());
            log.warn("로그인 유지 토큰 불일치로 회원의 세션과 토큰을 모두 끊음 member={}", stored.memberPublicId());
            // CookieTheftException은 필터 밖으로 던져져 500이 된다. 비로그인으로 이어가게 일반 실패로 끝낸다.
            throw new RememberMeAuthenticationException("로그인 유지 토큰이 맞지 않습니다.");
        }

        UserDetails user = getUserDetailsService().loadUserByUsername(stored.memberPublicId());
        // 권한이 바뀌면 토큰을 지우지만, 그 전에 남은 토큰이 있어도 관리자는 자동 로그인하지 않는다.
        if (isAdmin(user)) {
            tokenRepository.deleteSeries(series);
            throw new RememberMeAuthenticationException("관리자는 로그인 유지를 쓰지 않습니다.");
        }
        request.setAttribute(AUTO_LOGIN_ATTRIBUTE, Boolean.TRUE);
        return user;
    }

    private static boolean isAdmin(UserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(authority -> Role.ROLE_ADMIN.getKey().equals(authority.getAuthority()));
    }

    private boolean isPreviousTokenInGrace(
            RememberMeTokenRepository.StoredToken stored, String presentedHash, LocalDateTime now) {
        return stored.previousTokenHash() != null
                && sameHash(presentedHash, stored.previousTokenHash())
                && !stored.lastUsedAt().plus(ROTATION_GRACE).isBefore(now);
    }

    /** 이 기기의 토큰만 지운다. 다른 기기의 로그인 유지는 그대로 둔다. */
    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) {
        deletePresentedSeries(request);
        super.logout(request, response, authentication);
    }

    private void deletePresentedSeries(HttpServletRequest request) {
        String cookieValue = extractRememberMeCookie(request);
        if (cookieValue == null || cookieValue.isEmpty()) {
            return;
        }
        try {
            String[] tokens = decodeCookie(cookieValue);
            if (tokens.length == 2) {
                tokenRepository.deleteSeries(tokens[0]);
            }
        } catch (InvalidCookieException ignored) {
            // 형식이 틀린 쿠키는 표에 없는 값이라 지울 것이 없다.
        }
    }

    /** 30일 동안 쓰이지 않은 토큰을 지운다. 회원이 다시 오지 않으면 행이 남기 때문이다. */
    @Scheduled(cron = "${snowthing.remember-me.cleanup-cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredTokens() {
        try {
            int deleted =
                    tokenRepository.deleteLastUsedBefore(
                            LocalDateTime.now(clock).minus(TOKEN_VALIDITY));
            log.info("만료된 로그인 유지 토큰 삭제 count={}", deleted);
        } catch (DataAccessException exception) {
            log.error("만료된 로그인 유지 토큰 삭제 실패", exception);
        }
    }

    private String randomValue() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean sameHash(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
