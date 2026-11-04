package com.ikae.snowthing.domain.apitoken.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.apitoken.dto.ApiTokenCreateRequest;
import com.ikae.snowthing.domain.apitoken.dto.ApiTokenCreateResponse;
import com.ikae.snowthing.domain.apitoken.dto.ApiTokenResponse;
import com.ikae.snowthing.domain.apitoken.entity.ApiToken;
import com.ikae.snowthing.domain.apitoken.entity.ApiTokenScope;
import com.ikae.snowthing.domain.apitoken.repository.ApiTokenRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 제한 API 토큰 발급, 목록, 폐기, 인증.
 *
 * <p>토큰 형식은 {@code snt_} + 32바이트 난수의 base64url(패딩 없음, 43자)이다. 평문은 발급 응답에만 있고 로그에도 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiTokenService {

    public static final String TOKEN_PREFIX = "snt_";
    static final int MAX_ACTIVE_TOKENS = 10;
    static final int MAX_EXPIRES_IN_DAYS = 30;
    static final int MAX_NAME_LENGTH = 50;
    private static final int SECRET_BYTES = 32;
    private static final int DISPLAY_PREFIX_LENGTH = TOKEN_PREFIX.length() + 8;
    private static final Duration LAST_USED_UPDATE_INTERVAL = Duration.ofMinutes(1);
    private static final Pattern TOKEN_FORMAT = Pattern.compile("^snt_[A-Za-z0-9_-]{43}$");

    private final ApiTokenRepository tokens;
    private final MemberRepository members;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ApiTokenCreateResponse create(Long memberId, ApiTokenCreateRequest request) {
        Member owner =
                members.findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        if (!isActiveAdmin(owner)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        String name = validName(request == null ? null : request.name());
        Set<ApiTokenScope> scopes = validScopes(request == null ? null : request.scopes());
        int days = validDays(request == null ? null : request.expiresInDays());

        LocalDateTime now = LocalDateTime.now();
        if (tokens.countByMemberIdAndRevokedAtIsNullAndExpiresAtAfter(memberId, now)
                >= MAX_ACTIVE_TOKENS) {
            throw new CustomException(ErrorCode.API_TOKEN_LIMIT_EXCEEDED);
        }

        String plaintext = newPlaintextToken();
        ApiToken token =
                tokens.save(
                        ApiToken.issue(
                                owner,
                                name,
                                plaintext.substring(0, DISPLAY_PREFIX_LENGTH),
                                hash(plaintext),
                                scopes,
                                now,
                                now.plusDays(days)));
        return new ApiTokenCreateResponse(plaintext, ApiTokenResponse.from(token, now));
    }

    public List<ApiTokenResponse> list(Long memberId) {
        LocalDateTime now = LocalDateTime.now();
        return tokens.findByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
                .map(token -> ApiTokenResponse.from(token, now))
                .toList();
    }

    /** 본인 토큰만 폐기한다. 이미 폐기된 토큰은 그대로 돌려준다. */
    @Transactional
    public ApiTokenResponse revoke(Long memberId, String tokenPublicId) {
        ApiToken token =
                tokens.findByPublicIdAndMemberId(tokenPublicId, memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.API_TOKEN_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        token.revoke(now);
        return ApiTokenResponse.from(token, now);
    }

    /**
     * Bearer 토큰을 확인한다. 형식이 틀리거나, 없거나, 폐기되었거나, 만료되었거나, 소유자가 더 이상 활성 관리자가 아니면 빈 값을 돌려준다. 어느 이유인지는
     * 호출자에게 구분해 알려 주지 않는다.
     */
    @Transactional
    public Optional<ApiTokenUserDetails> authenticate(String rawToken) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {
            return Optional.empty();
        }
        Optional<ApiToken> found = tokens.findWithMemberByTokenHash(hash(rawToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ApiToken token = found.get();
        LocalDateTime now = LocalDateTime.now();
        if (!token.isUsableAt(now) || !isActiveAdmin(token.getMember())) {
            return Optional.empty();
        }
        ApiTokenUserDetails principal =
                new ApiTokenUserDetails(token.getMember(), token.getId(), token.scopeSet());
        tokens.touchLastUsed(token.getId(), now, now.minus(LAST_USED_UPDATE_INTERVAL));
        return Optional.of(principal);
    }

    public static boolean isActiveAdmin(Member member) {
        return member != null
                && member.getRole() == Role.ROLE_ADMIN
                && member.getStatus() == MemberStatus.ACTIVE;
    }

    public static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String newPlaintextToken() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            throw new CustomException(ErrorCode.API_TOKEN_INVALID_REQUEST);
        }
        return name;
    }

    private static Set<ApiTokenScope> validScopes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new CustomException(ErrorCode.API_TOKEN_INVALID_REQUEST);
        }
        Set<ApiTokenScope> scopes = EnumSet.noneOf(ApiTokenScope.class);
        for (String value : raw) {
            if (value == null) {
                throw new CustomException(ErrorCode.API_TOKEN_INVALID_REQUEST);
            }
            try {
                scopes.add(ApiTokenScope.valueOf(value.strip().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new CustomException(ErrorCode.API_TOKEN_INVALID_REQUEST);
            }
        }
        return scopes;
    }

    private static int validDays(Integer raw) {
        int days = raw == null ? MAX_EXPIRES_IN_DAYS : raw;
        if (days < 1 || days > MAX_EXPIRES_IN_DAYS) {
            throw new CustomException(ErrorCode.API_TOKEN_INVALID_REQUEST);
        }
        return days;
    }
}
