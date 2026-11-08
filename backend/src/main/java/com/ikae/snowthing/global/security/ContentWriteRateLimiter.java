package com.ikae.snowthing.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

/** 글, 댓글 같은 작성 요청을 작성자별로 제한해 연타와 도배를 막는다. 비로그인 작성은 IP로 묶는다. */
@Component
public class ContentWriteRateLimiter {
    public enum Action {
        POST(Duration.ofSeconds(10), 20),
        COMMENT(Duration.ofSeconds(3), 60),
        RESORT_REPORT(Duration.ofSeconds(10), 10),
        CARPOOL(Duration.ofSeconds(10), 10),
        MARKET_LISTING(Duration.ofSeconds(10), 10),
        DIRECT_MESSAGE(Duration.ofSeconds(5), 30),
        CONTENT_REPORT(Duration.ofSeconds(5), 30),
        // 비회원 기능이라 IP로 묶인다. 친구들이 같은 와이파이로 몰려 들어와도 막히지 않게 1초에 여러 건을 허용한다.
        CHARACTER_GROUP_CREATE(Duration.ofSeconds(1), 3, 60),
        CHARACTER_GROUP_JOIN(Duration.ofSeconds(1), 10, 300),
        // 비회원 글과 댓글의 수정, 삭제 비밀번호 확인. 최소 4자 비밀번호를 대입하지 못하게 하고 BCrypt 연산으로 CPU를 쓰는 요청을 묶는다.
        GUEST_PASSWORD(Duration.ofSeconds(1), 3, 30),
        // 비회원 추천은 쿠키로만 중복을 막아 쿠키를 지우면 다시 누를 수 있다. IP 단위 상한을 둔다.
        GUEST_REACTION(Duration.ofSeconds(1), 5, 120);

        // 짧은 간격마다 burstLimit건(기본 1건), 1시간마다 hourlyLimit건까지 허용한다. 서비스 검증에서 실패한 요청도 횟수를 쓰므로
        // 간격은 연타만 막을 만큼 짧게 둔다(오류를 고쳐 바로 다시 보내는 사용자를 막지 않도록).
        final Duration interval;
        final int burstLimit;
        final int hourlyLimit;

        Action(Duration interval, int hourlyLimit) {
            this(interval, 1, hourlyLimit);
        }

        Action(Duration interval, int burstLimit, int hourlyLimit) {
            this.interval = interval;
            this.burstLimit = burstLimit;
            this.hourlyLimit = hourlyLimit;
        }
    }

    private static final int GUEST_REACTIONS_PER_POST_PER_DAY = 4;

    private final SharedRequestLimiter sharedLimiter;
    // 테스트마다 같은 작성자로 연달아 쓰므로 꺼 두고, 통합 테스트만 공유 컨텍스트의 빈에서 잠시 켠다.
    private boolean enabled;

    public ContentWriteRateLimiter(
            SharedRequestLimiter sharedLimiter,
            @Value("${snowthing.write-limit.enabled:true}") boolean enabled) {
        this.sharedLimiter = sharedLimiter;
        this.enabled = enabled;
    }

    public void check(CustomUserDetails userDetails, String clientIp, Action action) {
        if (!enabled) return;
        String actor =
                userDetails != null
                        ? "member:" + userDetails.getUsername()
                        // IPv6는 /64로 묶어 주소를 바꿔 가며 우회하지 못하게 한다.
                        : "ip:" + ClientIpResolver.normalizeForLimit(clientIp);
        String prefix =
                "snowthing:limits:write:"
                        + action.name().toLowerCase(Locale.ROOT)
                        + ":"
                        // budget_key는 VARCHAR(100)이라 해시 앞 32자(128비트)만 쓴다.
                        + sha256(actor).substring(0, 32);
        var decision =
                sharedLimiter.acquire(
                        List.of(
                                new SharedRequestLimiter.Limit(
                                        prefix + ":burst", action.burstLimit, action.interval),
                                new SharedRequestLimiter.Limit(
                                        prefix + ":hour",
                                        action.hourlyLimit,
                                        Duration.ofHours(1))));
        if (!decision.allowed())
            throw new CustomException(
                    decision.available()
                            ? ErrorCode.CONTENT_WRITE_LIMIT
                            : ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
    }

    /**
     * 비회원 추천. IP 단위 상한에 더해 같은 IP가 한 글에 누를 수 있는 횟수를 하루 단위로 묶는다. 쿠키를 지워 가며 한 글의 추천을 올려 베스트에 보내지 못하게
     * 한다. 같은 통신사 IP를 나눠 쓰는 사람도 있어 한 번이 아니라 몇 번까지 허용한다.
     */
    public void checkGuestReaction(String clientIp, String postPublicId) {
        if (!enabled) return;
        Action action = Action.GUEST_REACTION;
        String actor = "ip:" + ClientIpResolver.normalizeForLimit(clientIp);
        String prefix = "snowthing:limits:write:guest_reaction:" + sha256(actor).substring(0, 32);
        String target =
                "snowthing:limits:write:guest_reaction_post:"
                        + sha256(actor + "|" + postPublicId).substring(0, 32);
        var decision =
                sharedLimiter.acquire(
                        List.of(
                                new SharedRequestLimiter.Limit(
                                        prefix + ":burst", action.burstLimit, action.interval),
                                new SharedRequestLimiter.Limit(
                                        prefix + ":hour", action.hourlyLimit, Duration.ofHours(1)),
                                new SharedRequestLimiter.Limit(
                                        target,
                                        GUEST_REACTIONS_PER_POST_PER_DAY,
                                        Duration.ofDays(1))));
        if (!decision.allowed())
            throw new CustomException(
                    decision.available()
                            ? ErrorCode.CONTENT_WRITE_LIMIT
                            : ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
