package com.ikae.snowthing.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.service.LoginAttemptLimiter;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

// 속성을 바꿔 컨텍스트를 새로 만들면 로컬 MySQL 연결 수가 넘치므로, 공유 컨텍스트의 빈에서 이 테스트 동안만 켠다.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoginAttemptLimitIntegrationTest {
    private static final String PASSWORD = "Password123!";
    private static final String WRONG_PASSWORD = "WrongPassword999!";
    private static final String IP = "203.0.113.10";
    private static final String OTHER_IP = "203.0.113.11";
    private static final String LOOPBACK = "127.0.0.1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired LoginAttemptLimiter limiter;

    private final List<Member> created = new ArrayList<>();
    private String email;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(limiter, "enabled", true);
        clearWindows();
        email = saveMember(MemberStatus.ACTIVE).getEmail();
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(limiter, "enabled", false);
        clearWindows();
        members.deleteAll(created);
    }

    @Test
    @DisplayName("5번 연속 실패하면 틀린 비밀번호와 맞는 비밀번호 모두 429로 거부한다")
    void blocksAfterFiveFailuresEvenWithCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertUnauthorized(login(email, WRONG_PASSWORD, IP));
        }

        assertBlocked(login(email, WRONG_PASSWORD, IP));
        assertBlocked(login(email, PASSWORD, IP));
    }

    @Test
    @DisplayName("차단 시간이 지나면 다시 로그인할 수 있다")
    void unblocksAfterWindowExpires() throws Exception {
        failFiveTimes(email, IP);
        assertBlocked(login(email, PASSWORD, IP));

        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=1 WHERE budget_key LIKE 'snowthing:limits:login:%'");

        assertThat(login(email, PASSWORD, IP).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("차단은 5번째 실패부터 15분이고, 거절된 시도는 차단을 늘리지 않는다")
    void blockLastsFifteenMinutesFromFifthFailure() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertUnauthorized(login(email, WRONG_PASSWORD, IP));
        }
        // 첫 실패에서 14분이 지나 만료가 1분 남은 상황을 만든다.
        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=? WHERE budget_key LIKE 'snowthing:limits:login:pair:%'",
                nowMillis() + 60_000);

        assertUnauthorized(login(email, WRONG_PASSWORD, IP));

        long remaining = pairExpiresAt() - nowMillis();
        assertThat(remaining).isBetween(14 * 60_000L, 15 * 60_000L);

        // 만료를 앞당겨도 거절된 시도가 다시 미루지 않는지 본다.
        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=? WHERE budget_key LIKE 'snowthing:limits:login:pair:%'",
                nowMillis() + 60_000);
        assertBlocked(login(email, PASSWORD, IP));
        assertThat(pairExpiresAt() - nowMillis()).isLessThanOrEqualTo(60_000L);
    }

    @Test
    @DisplayName("성공하면 실패 횟수를 초기화한다")
    void successResetsFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertUnauthorized(login(email, WRONG_PASSWORD, IP));
        }

        assertThat(login(email, PASSWORD, IP).getStatus()).isEqualTo(200);

        failFiveTimes(email, IP);
        assertBlocked(login(email, PASSWORD, IP));
    }

    @Test
    @DisplayName("차단은 이메일과 IP 조합에만 걸려 다른 IP의 본인과 같은 IP의 다른 회원은 막히지 않는다")
    void blockDoesNotAffectOtherIpOrOtherEmail() throws Exception {
        failFiveTimes(email, IP);
        assertBlocked(login(email, PASSWORD, IP));

        assertThat(login(email, PASSWORD, OTHER_IP).getStatus()).isEqualTo(200);
        String otherEmail = saveMember(MemberStatus.ACTIVE).getEmail();
        assertThat(login(otherEmail, PASSWORD, IP).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("없는 이메일과 있는 이메일은 실패와 차단 응답이 같고 같은 횟수로 차단된다")
    void unknownEmailGetsIdenticalResponses() throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        for (int i = 0; i < 5; i++) {
            assertSameResponse(
                    login(unknown, WRONG_PASSWORD, IP), login(email, WRONG_PASSWORD, IP));
        }

        MockHttpServletResponse unknownBlocked = login(unknown, WRONG_PASSWORD, IP);
        assertBlocked(unknownBlocked);
        assertSameResponse(unknownBlocked, login(email, WRONG_PASSWORD, IP));
    }

    @Test
    @DisplayName("정지, 탈퇴 계정은 500이 아니라 없는 이메일과 같은 401을 받는다")
    void inactiveAccountsGetSameResponseAsUnknownEmail() throws Exception {
        MockHttpServletResponse unknown =
                login("nobody-" + UUID.randomUUID() + "@example.com", WRONG_PASSWORD, IP);
        assertUnauthorized(unknown);

        for (MemberStatus status : List.of(MemberStatus.SUSPENDED, MemberStatus.WITHDRAWN)) {
            String inactive = saveMember(status).getEmail();
            assertSameResponse(unknown, login(inactive, PASSWORD, IP));
            assertSameResponse(unknown, login(inactive, WRONG_PASSWORD, IP));
        }
    }

    @Test
    @DisplayName("비밀번호가 없는 소셜 계정과 72바이트를 넘는 비밀번호도 500이 아니라 401을 받는다")
    void socialAccountAndLongPasswordFailWithUnauthorized() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member social =
                members.saveAndFlush(
                        Member.builder()
                                .email("social-" + suffix + "@example.com")
                                .password(null)
                                .nickname("소셜" + suffix)
                                .role(Role.ROLE_USER)
                                .build());
        created.add(social);

        assertUnauthorized(login(social.getEmail(), WRONG_PASSWORD, IP));
        assertUnauthorized(login(email, "a".repeat(200), IP));
        assertUnauthorized(login("nobody-" + suffix + "@example.com", "a".repeat(200), IP));
    }

    @Test
    @DisplayName("백엔드에 직접 들어온 요청의 전달 헤더를 바꿔도 차단을 우회하지 못한다")
    void forgedHeadersFromDirectConnectionAreIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertUnauthorized(forged(i, email, WRONG_PASSWORD, IP));
        }

        assertBlocked(forged(99, email, PASSWORD, IP));
    }

    @Test
    @DisplayName("Nginx를 거쳐도 클라이언트가 붙인 X-Forwarded-For 앞부분으로는 차단을 우회하지 못한다")
    void forgedForwardedForBehindProxyIsIgnored() throws Exception {
        for (int i = 0; i < 5; i++) {
            int attempt = i;
            assertUnauthorized(
                    login(
                            email,
                            WRONG_PASSWORD,
                            LOOPBACK,
                            builder ->
                                    builder.header("X-Real-IP", IP)
                                            .header("X-Forwarded-For", "198.51.100." + attempt)
                                            .header("CF-Connecting-IP", "198.51.100." + attempt)));
        }

        assertBlocked(login(email, PASSWORD, LOOPBACK, builder -> builder.header("X-Real-IP", IP)));
    }

    @Test
    @DisplayName("IP 형식이 아닌 값을 바꿔 가며 보내도 새 횟수를 얻지 못한다")
    void malformedIpValuesShareOneBucket() throws Exception {
        for (int i = 0; i < 5; i++) {
            int attempt = i;
            assertUnauthorized(
                    login(
                            email,
                            WRONG_PASSWORD,
                            LOOPBACK,
                            builder -> builder.header("X-Real-IP", "not-an-ip-" + attempt)));
        }

        assertBlocked(
                login(
                        email,
                        PASSWORD,
                        LOOPBACK,
                        builder -> builder.header("X-Real-IP", "not-an-ip-99")));
    }

    @Test
    @DisplayName("대소문자와 공백을 바꿔도 같은 이메일로 센다")
    void emailIsNormalizedBeforeCounting() throws Exception {
        assertUnauthorized(login(email, WRONG_PASSWORD, IP));
        assertUnauthorized(login(email.toUpperCase(), WRONG_PASSWORD, IP));
        assertUnauthorized(login(" " + email, WRONG_PASSWORD, IP));
        assertUnauthorized(login(email + " ", WRONG_PASSWORD, IP));
        assertUnauthorized(login(email, WRONG_PASSWORD, IP));

        assertBlocked(login(email, PASSWORD, IP));
    }

    @Test
    @DisplayName("collation이 같은 회원으로 찾는 악센트, 제어 문자 표기를 바꿔도 새 횟수를 얻지 못한다")
    void collationVariantsCannotBypassCounting() throws Exception {
        String accented = email.replaceFirst("m", "ḿ");
        assertThat(accented).isNotEqualTo(email);
        // 전제 확인: 이 표기로도 같은 회원의 비밀번호를 검사한다. 이 줄이 실패하면 우회 위험 자체가 없다는 뜻이다.
        assertThat(login(accented, PASSWORD, IP).getStatus()).isEqualTo(200);

        List<String> variants =
                List.of(
                        email.replaceFirst("m", "ḿ"),
                        email.replaceFirst("m", "ṁ"),
                        email.replaceFirst("m", "ṃ"),
                        email.replaceFirst("m", "m\u0001"),
                        email.replaceFirst("m", "m\u0002"));
        for (String variant : variants) {
            assertUnauthorized(login(variant, WRONG_PASSWORD, IP));
        }

        assertBlocked(login(email.replaceFirst("m", "m\u0003"), WRONG_PASSWORD, IP));
    }

    @Test
    @DisplayName("같은 IP에서 여러 이메일로 30번 실패하면 그 IP의 로그인을 막고, 다른 IP는 막지 않는다")
    void ipCapBlocksSprayingAcrossEmails() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertUnauthorized(login("spray" + i + "@example.com", WRONG_PASSWORD, IP));
        }

        assertBlocked(login("spray-next@example.com", WRONG_PASSWORD, IP));
        assertBlocked(login(email, PASSWORD, IP));
        assertThat(login(email, PASSWORD, OTHER_IP).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("자기 계정으로 성공해도 IP 실패 횟수는 지워지지 않고, 성공 자체는 IP 횟수를 쓰지 않는다")
    void successDoesNotClearIpCapAndDoesNotConsumeIt() throws Exception {
        for (int i = 0; i < 29; i++) {
            assertUnauthorized(login("spray" + i + "@example.com", WRONG_PASSWORD, IP));
        }
        for (int i = 0; i < 3; i++) {
            assertThat(login(email, PASSWORD, IP).getStatus()).isEqualTo(200);
        }

        assertUnauthorized(login("spray-last@example.com", WRONG_PASSWORD, IP));
        assertBlocked(login("spray-next@example.com", WRONG_PASSWORD, IP));
    }

    @Test
    @DisplayName("동시에 보내도 5번까지만 비밀번호를 검사한다")
    void concurrentAttemptsCannotExceedLimit() throws Exception {
        int requests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                tasks.add(() -> login(email, WRONG_PASSWORD, IP).getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : executor.invokeAll(tasks)) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(status -> status == 401).hasSize(5);
            assertThat(statuses).filteredOn(status -> status == 429).hasSize(requests - 5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("여러 IP로 나눠 한 계정에 10번 실패하면 새 IP의 맞는 비밀번호도 막고, 다른 계정은 막지 않는다")
    void accountLimitBlocksDistributedFailures() throws Exception {
        failAcrossIps(email, 10);

        assertBlocked(login(email, PASSWORD, "198.51.100.200"));
        String otherEmail = saveMember(MemberStatus.ACTIVE).getEmail();
        assertThat(login(otherEmail, PASSWORD, "198.51.100.200").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("계정 짧은 창이 끝나면 다시 로그인할 수 있고, 성공하면 계정 횟수가 초기화된다")
    void accountShortWindowExpiresAndSuccessResets() throws Exception {
        failAcrossIps(email, 10);
        assertBlocked(login(email, PASSWORD, "198.51.100.200"));

        jdbc.update(
                "UPDATE request_budget_window SET expires_at_millis=1 WHERE budget_key LIKE 'snowthing:limits:login:account:short:%'");
        assertThat(login(email, PASSWORD, "198.51.100.200").getStatus()).isEqualTo(200);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:login:account:%'",
                                Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("짧은 창을 거듭 넘겨 한 시간에 30번 실패하면 짧은 창이 끝나도 계속 막는다")
    void accountLongWindowEscalates() throws Exception {
        for (int round = 0; round < 3; round++) {
            failAcrossIps(email, 10, round * 2);
            jdbc.update(
                    "UPDATE request_budget_window SET expires_at_millis=1 WHERE budget_key LIKE 'snowthing:limits:login:account:short:%'");
        }

        assertBlocked(login(email, PASSWORD, "198.51.100.200"));
    }

    @Test
    @DisplayName("이메일 원문과 비밀번호는 로그와 제한 키에 남지 않는다")
    void credentialsAreNotExposed(CapturedOutput output) throws Exception {
        failFiveTimes(email, IP);
        assertBlocked(login(email, WRONG_PASSWORD, IP));

        assertThat(output.getAll()).contains("로그인 시도 제한 발동").doesNotContain(email, WRONG_PASSWORD);
        List<String> keys =
                jdbc.queryForList(
                        "SELECT budget_key FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:login:%'",
                        String.class);
        assertThat(keys).isNotEmpty().noneMatch(key -> key.contains(email) || key.contains(IP));
        assertThat(keys).allMatch(key -> key.length() <= 100);
    }

    // IP마다 5번 미만으로 나눠 실패해 조합 제한에 걸리지 않고 계정 횟수만 쌓는다.
    private void failAcrossIps(String targetEmail, int failures) throws Exception {
        failAcrossIps(targetEmail, failures, 0);
    }

    private void failAcrossIps(String targetEmail, int failures, int ipOffset) throws Exception {
        for (int i = 0; i < failures; i++) {
            String ip = "198.51.100." + (10 + ipOffset * 3 + i / 4);
            assertUnauthorized(login(targetEmail, WRONG_PASSWORD, ip));
        }
    }

    private void failFiveTimes(String targetEmail, String ip) throws Exception {
        for (int i = 0; i < 5; i++) {
            assertUnauthorized(login(targetEmail, WRONG_PASSWORD, ip));
        }
    }

    private MockHttpServletResponse forged(int seed, String targetEmail, String password, String ip)
            throws Exception {
        String fake = "198.51.100." + seed;
        return login(
                targetEmail,
                password,
                ip,
                builder ->
                        builder.header("X-Real-IP", fake)
                                .header("X-Forwarded-For", fake)
                                .header("CF-Connecting-IP", fake));
    }

    private MockHttpServletResponse login(String targetEmail, String password, String remoteAddr)
            throws Exception {
        return login(targetEmail, password, remoteAddr, builder -> builder);
    }

    private MockHttpServletResponse login(
            String targetEmail,
            String password,
            String remoteAddr,
            java.util.function.UnaryOperator<MockHttpServletRequestBuilder> customizer)
            throws Exception {
        MockHttpServletRequestBuilder builder =
                post("/api/v1/auth/login")
                        .with(csrf())
                        .with(
                                request -> {
                                    request.setRemoteAddr(remoteAddr);
                                    return request;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        MemberLoginRequest.builder()
                                                .email(targetEmail)
                                                .password(password)
                                                .build()));
        return mvc.perform(customizer.apply(builder)).andReturn().getResponse();
    }

    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(code(response)).isEqualTo("AUTH_001");
    }

    private void assertBlocked(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(code(response)).isEqualTo("AUTH_013");
        assertThat(objectMapper.readTree(body(response)).get("message").asText())
                .isEqualTo("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
    }

    private void assertSameResponse(MockHttpServletResponse first, MockHttpServletResponse second)
            throws Exception {
        assertThat(second.getStatus()).isEqualTo(first.getStatus());
        assertThat(body(second)).isEqualTo(body(first));
    }

    private String code(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(body(response)).get("code").asText();
    }

    private String body(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private long nowMillis() {
        return jdbc.queryForObject(
                "SELECT FLOOR(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(6))*1000)", Long.class);
    }

    private long pairExpiresAt() {
        return jdbc.queryForObject(
                "SELECT expires_at_millis FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:login:pair:%'",
                Long.class);
    }

    private void clearWindows() {
        jdbc.update(
                "DELETE FROM request_budget_window WHERE budget_key LIKE 'snowthing:limits:login:%'");
    }

    private Member saveMember(MemberStatus status) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                members.saveAndFlush(
                        Member.builder()
                                .email("member-" + suffix + "@example.com")
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname("제한" + suffix)
                                .role(Role.ROLE_USER)
                                .status(status)
                                .build());
        created.add(member);
        return member;
    }
}
