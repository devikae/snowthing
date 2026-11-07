package com.ikae.snowthing.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.SharedRequestLimiter;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 비밀번호 대입을 막는다. 이메일과 IP 조합으로 세어 다른 사람이 남의 계정을 잠그지 못하게 하고, 같은 IP에서 여러 이메일을 돌려 보는 경우는 IP 단독 상한으로
 * 막는다. 회원 존재 여부를 보지 않고 입력한 이메일로만 세므로 없는 이메일도 같은 응답을 받는다.
 *
 * <p>인증 전에 횟수를 먼저 쓰고 성공하면 돌려준다. 동시에 보낸 요청이 검사를 함께 통과해 상한을 넘겨 시도하는 일을 막기 위해서다.
 */
@Slf4j
@Component
public class LoginAttemptLimiter {
    static final int MAX_ATTEMPTS = 5;
    static final int MAX_ATTEMPTS_PER_IP = 30;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private static final String PAIR_PREFIX = "snowthing:limits:login:pair:";
    private static final String IP_PREFIX = "snowthing:limits:login:ip:";
    private static final String UNCOMMON_EMAIL = "uncommon-email";

    private final SharedRequestLimiter sharedLimiter;
    private final EmailNormalizer emailNormalizer;
    // 테스트마다 같은 IP로 로그인을 반복하므로 꺼 두고, 통합 테스트만 공유 컨텍스트의 빈에서 잠시 켠다.
    private boolean enabled;

    public LoginAttemptLimiter(
            SharedRequestLimiter sharedLimiter,
            EmailNormalizer emailNormalizer,
            @Value("${snowthing.login-limit.enabled:true}") boolean enabled) {
        this.sharedLimiter = sharedLimiter;
        this.emailNormalizer = emailNormalizer;
        this.enabled = enabled;
    }

    public void acquire(String email, String clientIp) {
        if (!enabled) return;
        String pairKey = pairKey(email, clientIp);
        String ipKey = ipKey(clientIp);
        var decision =
                sharedLimiter.acquire(
                        List.of(
                                // 5번째 시도부터 15분을 재도록 쓸 때마다 만료를 미룬다. 거절된 요청은 미루지 않는다.
                                new SharedRequestLimiter.Limit(pairKey, MAX_ATTEMPTS, WINDOW, true),
                                // 공용 IP에서 드문 실패가 쌓여 막히지 않도록 고정 창으로 센다.
                                new SharedRequestLimiter.Limit(
                                        ipKey, MAX_ATTEMPTS_PER_IP, WINDOW)));
        if (!decision.available()) {
            throw new CustomException(ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
        }
        if (!decision.allowed()) {
            log.warn("로그인 시도 제한 발동: scope={}", ipKey.equals(decision.deniedKey()) ? "ip" : "pair");
            throw new CustomException(ErrorCode.LOGIN_ATTEMPT_LIMIT);
        }
    }

    /**
     * 성공하면 조합 횟수는 초기화하고 IP 횟수는 이번 시도만 돌려준다. IP 횟수까지 지우면 공격자가 자기 계정으로 한 번 로그인해 상한을 없앨 수 있다. 정리에 실패해도
     * 비밀번호는 맞았으므로 로그인은 그대로 진행한다.
     */
    public void succeeded(String email, String clientIp) {
        if (!enabled) return;
        try {
            sharedLimiter.reset(pairKey(email, clientIp));
            sharedLimiter.refund(ipKey(clientIp));
        } catch (DataAccessException exception) {
            log.warn("로그인 시도 횟수 정리 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private String pairKey(String email, String clientIp) {
        return PAIR_PREFIX
                + hash(emailKey(email) + "\n" + ClientIpResolver.normalizeForLimit(clientIp));
    }

    private String ipKey(String clientIp) {
        return IP_PREFIX + hash(ClientIpResolver.normalizeForLimit(clientIp));
    }

    /**
     * 회원 조회 컬럼의 collation은 악센트와 제어 문자를 무시하므로 {@code üser@}도 {@code user@}와 같은 회원을 찾는다. 이런 표기를 바꿔 가며
     * 새 횟수를 얻지 못하도록 ASCII 표기가 아닌 이메일은 IP별로 한 곳에 묶는다.
     */
    private String emailKey(String email) {
        String normalized = emailNormalizer.normalize(email);
        return normalized.chars().allMatch(c -> c > 0x20 && c < 0x7F) ? normalized : UNCOMMON_EMAIL;
    }

    // budget_key는 VARCHAR(100)이고 이메일 원문을 DB에 남기지 않기 위해 해시 앞 32자(128비트)만 쓴다.
    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
