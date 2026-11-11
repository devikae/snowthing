package com.ikae.snowthing.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>여러 IP로 나눠 한 계정을 노리는 경우는 IP와 무관한 계정 상한 두 단계로 막는다. 짧은 창(기본 15분 10회)을 넘기면 그 창이 끝날 때까지, 긴 창(기본 1시간
 * 30회)을 넘기면 한 시간이 끝날 때까지 비밀번호 로그인을 받지 않는다. 고정 창이라 남이 일부러 잠가도 최대 한 시간 안에 풀리고, 소셜 로그인과 비밀번호 재설정은 그대로
 * 쓸 수 있으며 재설정을 마치면 계정 횟수를 지운다.
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
    private static final String ACCOUNT_SHORT_PREFIX = "snowthing:limits:login:account:short:";
    private static final String ACCOUNT_LONG_PREFIX = "snowthing:limits:login:account:long:";
    private static final String UNCOMMON_EMAIL = "uncommon-email";

    private final SharedRequestLimiter sharedLimiter;
    private final EmailNormalizer emailNormalizer;
    private final AccountLimits accountLimits;
    // 테스트마다 같은 IP로 로그인을 반복하므로 꺼 두고, 통합 테스트만 공유 컨텍스트의 빈에서 잠시 켠다.
    private boolean enabled;

    /** IP와 무관하게 한 계정에 허용하는 실패 횟수. 짧은 창을 넘기면 짧게, 긴 창까지 넘기면 길게 잠긴다. */
    public record AccountLimits(
            int shortMaxFailures, Duration shortWindow, int longMaxFailures, Duration longWindow) {
        public static final AccountLimits DEFAULT =
                new AccountLimits(10, Duration.ofMinutes(15), 30, Duration.ofHours(1));
    }

    @Autowired
    public LoginAttemptLimiter(
            SharedRequestLimiter sharedLimiter,
            EmailNormalizer emailNormalizer,
            @Value("${snowthing.login-limit.enabled:true}") boolean enabled,
            @Value("${snowthing.login-limit.account.short-max-failures:10}") int shortMaxFailures,
            @Value("${snowthing.login-limit.account.short-window:15m}") Duration shortWindow,
            @Value("${snowthing.login-limit.account.long-max-failures:30}") int longMaxFailures,
            @Value("${snowthing.login-limit.account.long-window:1h}") Duration longWindow) {
        this(
                sharedLimiter,
                emailNormalizer,
                enabled,
                new AccountLimits(shortMaxFailures, shortWindow, longMaxFailures, longWindow));
    }

    LoginAttemptLimiter(
            SharedRequestLimiter sharedLimiter,
            EmailNormalizer emailNormalizer,
            boolean enabled,
            AccountLimits accountLimits) {
        this.sharedLimiter = sharedLimiter;
        this.emailNormalizer = emailNormalizer;
        this.enabled = enabled;
        this.accountLimits = accountLimits;
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
                                new SharedRequestLimiter.Limit(ipKey, MAX_ATTEMPTS_PER_IP, WINDOW),
                                // 계정 상한은 고정 창이다. 거절된 시도는 창을 미루지 않아 잠금이 끝없이 길어지지 않는다.
                                new SharedRequestLimiter.Limit(
                                        accountShortKey(email),
                                        accountLimits.shortMaxFailures(),
                                        accountLimits.shortWindow()),
                                new SharedRequestLimiter.Limit(
                                        accountLongKey(email),
                                        accountLimits.longMaxFailures(),
                                        accountLimits.longWindow())));
        if (!decision.available()) {
            throw new CustomException(ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
        }
        if (!decision.allowed()) {
            log.warn("로그인 시도 제한 발동: scope={}", scope(decision.deniedKey(), ipKey));
            throw new CustomException(ErrorCode.LOGIN_ATTEMPT_LIMIT);
        }
    }

    private static String scope(String deniedKey, String ipKey) {
        if (ipKey.equals(deniedKey)) return "ip";
        if (deniedKey != null && deniedKey.startsWith(ACCOUNT_SHORT_PREFIX)) return "account-short";
        if (deniedKey != null && deniedKey.startsWith(ACCOUNT_LONG_PREFIX)) return "account-long";
        return "pair";
    }

    /** 비밀번호 재설정을 마치면 계정 잠금을 푼다. 남이 일부러 잠근 계정도 메일함 주인은 바로 되찾을 수 있다. */
    public void clearAccount(String email) {
        if (!enabled) return;
        try {
            sharedLimiter.reset(accountShortKey(email));
            sharedLimiter.reset(accountLongKey(email));
        } catch (DataAccessException exception) {
            log.warn("계정 로그인 시도 횟수 정리 실패: {}", exception.getClass().getSimpleName());
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
            sharedLimiter.reset(accountShortKey(email));
            sharedLimiter.reset(accountLongKey(email));
            sharedLimiter.refund(ipKey(clientIp));
        } catch (DataAccessException exception) {
            log.warn("로그인 시도 횟수 정리 실패: {}", exception.getClass().getSimpleName());
        }
    }

    private String pairKey(String email, String clientIp) {
        return PAIR_PREFIX
                + hash(emailKey(email) + "\n" + ClientIpResolver.normalizeForLimit(clientIp));
    }

    private String accountShortKey(String email) {
        return ACCOUNT_SHORT_PREFIX + hash(accountEmailKey(email));
    }

    private String accountLongKey(String email) {
        return ACCOUNT_LONG_PREFIX + hash(accountEmailKey(email));
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

    /**
     * 계정 키는 IP와 무관하므로 악센트, 제어 문자 표기를 모두 한 곳에 묶으면 그런 표기를 한 번도 쓰지 않는 회원까지 함께 잠긴다. 그래서 먼저 결합 문자와 제어
     * 문자를 지워 collation이 같은 회원으로 찾는 표기를 원래 계정 키로 모으고, 그래도 ASCII가 아닌 표기만 한 곳에 묶는다.
     */
    private String accountEmailKey(String email) {
        String folded =
                Normalizer.normalize(emailNormalizer.normalize(email), Normalizer.Form.NFD)
                        .replaceAll("[\\p{Mn}\\p{Cc}]", "");
        return folded.chars().allMatch(c -> c > 0x20 && c < 0x7F) ? folded : UNCOMMON_EMAIL;
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
