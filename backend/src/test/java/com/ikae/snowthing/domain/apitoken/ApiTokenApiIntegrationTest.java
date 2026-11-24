package com.ikae.snowthing.domain.apitoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 제한 API 토큰의 발급, 목록, 폐기, 사용 규칙 (IKA-118).
 *
 * <p>기준 문서: docs/conception/017_ai_answer_assist/06_answer_draft_flow.md. 초안 저장과 게시 흐름은 {@code
 * AnswerDraftFlowIntegrationTest}가 다루고, 여기서는 토큰 자체의 경계값과 "토큰으로 가면 안 되는 곳"을 고정한다.
 *
 * <p>테스트 트랜잭션 안에서 돌고 끝나면 되돌린다. JDBC로 행을 직접 바꾼 뒤에는 영속성 컨텍스트를 비워 서비스가 바뀐 값을 읽게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ApiTokenApiIntegrationTest {

    private static final String TOKENS = "/api/v1/admin/api-tokens";
    private static final String DRAFTS = "/api/v1/answer-drafts";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MemberRepository members;

    private Member admin;
    private Member otherAdmin;
    private Member user;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString();
        admin = saveMember(unique, "a", Role.ROLE_ADMIN);
        otherAdmin = saveMember(unique, "b", Role.ROLE_ADMIN);
        user = saveMember(unique, "u", Role.ROLE_USER);
    }

    @Nested
    @DisplayName("발급")
    class Issue {

        @Test
        @DisplayName("만료 일수를 비우면 30일이고, 응답은 캐시하지 않으며 평문은 snt_ 뒤 43자다")
        void defaultsToThirtyDays() throws Exception {
            JsonNode created =
                    json(
                            create(admin, body("mcp-local", List.of("READ", "DRAFT"), null))
                                    .andExpect(status().isCreated())
                                    .andExpect(
                                            header().string(
                                                            HttpHeaders.CACHE_CONTROL,
                                                            "no-store")));

            String plaintext = created.get("token").asText();
            JsonNode token = created.get("apiToken");
            assertThat(plaintext).matches("^snt_[A-Za-z0-9_-]{43}$");
            assertThat(token.get("tokenPrefix").asText()).isEqualTo(plaintext.substring(0, 12));
            assertThat(token.get("active").asBoolean()).isTrue();
            assertThat(token.get("revokedAt").isNull()).isTrue();
            assertThat(token.get("lastUsedAt").isNull()).isTrue();
            assertThat(scopes(token)).containsExactly("READ", "DRAFT");
            LocalDateTime createdAt = LocalDateTime.parse(token.get("createdAt").asText());
            LocalDateTime expiresAt = LocalDateTime.parse(token.get("expiresAt").asText());
            assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofDays(30));
        }

        @Test
        @DisplayName("경계값: 이름 50자, 만료 1일과 30일은 받는다")
        void acceptsBoundaryValues() throws Exception {
            create(admin, body("가".repeat(50), List.of("READ"), 1)).andExpect(status().isCreated());
            JsonNode longest =
                    json(
                            create(admin, body("  앞뒤 공백  ", List.of("DRAFT"), 30))
                                    .andExpect(status().isCreated()));

            assertThat(longest.get("apiToken").get("name").asText()).isEqualTo("앞뒤 공백");
        }

        @Test
        @DisplayName("범위는 대소문자와 앞뒤 공백을 가리지 않고, 같은 범위를 두 번 보내도 하나다")
        void normalizesScopes() throws Exception {
            JsonNode created =
                    json(
                            create(admin, body("mcp", List.of(" read ", "READ", "draft"), 7))
                                    .andExpect(status().isCreated()));

            assertThat(scopes(created.get("apiToken"))).containsExactly("READ", "DRAFT");
        }

        @Test
        @DisplayName("이름, 범위, 만료 일수가 규칙을 벗어나면 400이고 토큰은 만들어지지 않는다")
        void rejectsInvalidRequests() throws Exception {
            List<Map<String, Object>> invalid = new ArrayList<>();
            invalid.add(body(null, List.of("READ"), 7));
            invalid.add(body("   ", List.of("READ"), 7));
            invalid.add(body("가".repeat(51), List.of("READ"), 7));
            invalid.add(body("mcp", null, 7));
            invalid.add(body("mcp", List.of(), 7));
            invalid.add(body("mcp", List.of("PUBLISH"), 7));
            invalid.add(body("mcp", List.of("READ", "ROLE_ADMIN"), 7));
            invalid.add(body("mcp", Arrays.asList("READ", null), 7));
            invalid.add(body("mcp", List.of("READ"), 0));
            invalid.add(body("mcp", List.of("READ"), -1));
            invalid.add(body("mcp", List.of("READ"), 31));

            for (Map<String, Object> request : invalid) {
                create(admin, request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("API_TOKEN_003"));
            }
            mockMvc.perform(post(TOKENS).with(csrf()).with(user(details(admin))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_003"));

            assertThat(tokenCount(admin)).isZero();
        }

        @Test
        @DisplayName("사용 중인 토큰은 10개까지다. 폐기하거나 만료된 토큰은 세지 않는다")
        void capsActiveTokens() throws Exception {
            List<String> tokenIds = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tokenIds.add(
                        json(create(admin, body("mcp-" + i, List.of("READ"), 7))
                                        .andExpect(status().isCreated()))
                                .get("apiToken")
                                .get("tokenId")
                                .asText());
            }
            create(admin, body("mcp-over", List.of("READ"), 7))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_004"));
            // 상한은 회원별이다.
            create(otherAdmin, body("other", List.of("READ"), 7)).andExpect(status().isCreated());

            revoke(admin, tokenIds.get(0)).andExpect(status().isOk());
            create(admin, body("after-revoke", List.of("READ"), 7)).andExpect(status().isCreated());
            create(admin, body("over-again", List.of("READ"), 7)).andExpect(status().isConflict());

            jdbc.update(
                    "UPDATE api_token SET expires_at = ? WHERE public_id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                    tokenIds.get(1));
            entityManager.clear();
            create(admin, body("after-expiry", List.of("READ"), 7)).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("비로그인 401, 일반 회원 403, CSRF 토큰 없는 관리자 403")
        void requiresAdminSession() throws Exception {
            String request = write(body("mcp", List.of("READ"), 7));

            mockMvc.perform(
                            post(TOKENS)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            post(TOKENS)
                                    .with(csrf())
                                    .with(user(details(user)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .andExpect(status().isForbidden());
            mockMvc.perform(
                            post(TOKENS)
                                    .with(user(details(admin)))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(request))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(TOKENS)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(TOKENS).with(user(details(user))))
                    .andExpect(status().isForbidden());

            assertThat(tokenCount(admin)).isZero();
            assertThat(tokenCount(user)).isZero();
        }

        @Test
        @DisplayName("정지된 관리자는 세션이 남아 있어도 토큰을 만들 수 없다")
        void suspendedAdminCannotIssue() throws Exception {
            CustomUserDetails staleSession = details(admin);
            jdbc.update(
                    "UPDATE member SET status = 'SUSPENDED' WHERE member_id = ?", admin.getId());
            entityManager.clear();

            mockMvc.perform(
                            post(TOKENS)
                                    .with(csrf())
                                    .with(user(staleSession))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(write(body("mcp", List.of("READ"), 7))))
                    .andExpect(status().isForbidden());

            assertThat(tokenCount(admin)).isZero();
        }
    }

    @Nested
    @DisplayName("목록과 폐기")
    class ListAndRevoke {

        @Test
        @DisplayName("목록은 내 토큰만 최신순으로 보여 주고 평문과 해시 필드가 없다")
        void listsOnlyOwnTokensWithoutSecrets() throws Exception {
            String first = createToken(admin, "READ");
            String second = createToken(admin, "DRAFT");
            String others = createToken(otherAdmin, "READ");
            // 같은 시각에 만들어져도 ID 내림차순이라 나중 것이 먼저다.
            jdbc.update(
                    "UPDATE api_token SET created_at = ? WHERE member_id = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusHours(1)),
                    admin.getId());
            entityManager.clear();

            JsonNode list =
                    json(
                            mockMvc.perform(get(TOKENS).with(user(details(admin))))
                                    .andExpect(status().isOk()));

            assertThat(list).hasSize(2);
            assertThat(list.get(0).get("tokenId").asText()).isEqualTo(tokenIdOf(second));
            assertThat(list.get(1).get("tokenId").asText()).isEqualTo(tokenIdOf(first));
            List<String> fields = new ArrayList<>();
            list.get(0).fieldNames().forEachRemaining(fields::add);
            assertThat(fields)
                    .containsExactlyInAnyOrder(
                            "tokenId",
                            "name",
                            "tokenPrefix",
                            "scopes",
                            "createdAt",
                            "expiresAt",
                            "revokedAt",
                            "lastUsedAt",
                            "active");
            String raw = list.toString();
            for (String plaintext : List.of(first, second, others)) {
                assertThat(raw)
                        .doesNotContain(plaintext.substring(12))
                        .doesNotContain(ApiTokenService.hash(plaintext));
            }
        }

        @Test
        @DisplayName("다른 관리자의 토큰은 폐기할 수 없고(404) 그 토큰은 계속 쓸 수 있다")
        void cannotRevokeOthersToken() throws Exception {
            String token = createToken(admin, "DRAFT");

            revoke(otherAdmin, tokenIdOf(token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_002"));
            revoke(admin, UUID.randomUUID().toString())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_002"));

            entityManager.clear();
            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("DRAFT_001"));
        }

        @Test
        @DisplayName("폐기하면 바로 401이고, 다시 폐기해도 처음 폐기 시각이 그대로다")
        void revokeIsImmediateAndIdempotent() throws Exception {
            String token = createToken(admin, "DRAFT");
            String tokenId = tokenIdOf(token);

            String revokedAt =
                    json(revoke(admin, tokenId)
                                    .andExpect(status().isOk())
                                    .andExpect(jsonPath("$.active").value(false)))
                            .get("revokedAt")
                            .asText();
            entityManager.flush();
            entityManager.clear();

            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_001"));
            String again =
                    json(revoke(admin, tokenId).andExpect(status().isOk()))
                            .get("revokedAt")
                            .asText();
            assertThat(LocalDateTime.parse(again))
                    .isCloseTo(LocalDateTime.parse(revokedAt), within(1, ChronoUnit.MILLIS));
        }

        @Test
        @DisplayName("폐기는 비로그인 401, 일반 회원 403이다")
        void revokeRequiresAdminSession() throws Exception {
            String tokenId = tokenIdOf(createToken(admin, "READ"));

            mockMvc.perform(post(TOKENS + "/{id}/revoke", tokenId).with(csrf()))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            post(TOKENS + "/{id}/revoke", tokenId)
                                    .with(csrf())
                                    .with(user(details(user))))
                    .andExpect(status().isForbidden());

            assertThat(
                            jdbc.queryForObject(
                                    "SELECT revoked_at FROM api_token WHERE public_id = ?",
                                    Timestamp.class,
                                    tokenId))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("토큰으로 접근")
    class Use {

        @Test
        @DisplayName("범위를 모두 가진 토큰도 허용 경로 밖(알림, 쪽지, 스크랩, 푸시, 글쓰기, 관리자 API)은 403이다")
        void tokenCannotLeaveAllowedRoutes() throws Exception {
            String token = createToken(admin, "READ", "DRAFT");
            String draftId = UUID.randomUUID().toString();
            List<Route> blocked =
                    List.of(
                            new Route(HttpMethod.GET, "/api/v1/notifications"),
                            new Route(HttpMethod.GET, "/api/v1/notifications/unread-count"),
                            new Route(HttpMethod.PUT, "/api/v1/notifications/read-all"),
                            new Route(HttpMethod.GET, "/api/v1/members/me/notification-settings"),
                            new Route(HttpMethod.GET, "/api/v1/members/me/scraps"),
                            new Route(HttpMethod.PUT, "/api/v1/push/subscriptions"),
                            new Route(HttpMethod.GET, "/api/v1/messages/received"),
                            new Route(HttpMethod.GET, "/api/v1/messages/unread-count"),
                            new Route(HttpMethod.POST, "/api/v1/messages"),
                            new Route(HttpMethod.GET, "/api/v1/season-logs/me"),
                            // 문의 게시판을 포함한 게시글 읽기와 쓰기. 비로그인에게 열린 경로도 토큰으로는 못 간다.
                            new Route(HttpMethod.GET, "/api/v1/posts"),
                            new Route(HttpMethod.POST, "/api/v1/posts"),
                            new Route(HttpMethod.POST, "/api/v1/comments"),
                            new Route(HttpMethod.POST, "/api/v1/reports"),
                            new Route(HttpMethod.GET, TOKENS),
                            new Route(HttpMethod.POST, TOKENS),
                            new Route(HttpMethod.GET, "/api/v1/admin/reports"),
                            new Route(HttpMethod.GET, "/api/v1/admin/answer-drafts"),
                            new Route(
                                    HttpMethod.POST,
                                    "/api/v1/admin/answer-drafts/" + draftId + "/publish"),
                            // 허용 경로와 비슷하지만 메서드나 모양이 다른 것.
                            new Route(HttpMethod.GET, DRAFTS),
                            new Route(HttpMethod.PUT, DRAFTS),
                            new Route(HttpMethod.DELETE, DRAFTS + "/" + draftId),
                            new Route(HttpMethod.GET, DRAFTS + "/" + draftId + "/"),
                            new Route(HttpMethod.GET, DRAFTS + "/" + draftId + "/audits"),
                            new Route(HttpMethod.POST, "/api/v1/admin/search/evidence"),
                            new Route(HttpMethod.GET, "/api/v1/admin/search/evidence/C:1:0/raw"),
                            new Route(HttpMethod.GET, "/api/v1/admin/search"));

            for (Route route : blocked) {
                mockMvc.perform(withToken(route.method(), route.path(), token).content("{}"))
                        .andExpect(
                                result ->
                                        assertThat(result.getResponse().getStatus())
                                                .as(route.method() + " " + route.path())
                                                .isEqualTo(403))
                        .andExpect(jsonPath("$.code").value("AUTH_002"));
            }
        }

        @Test
        @DisplayName("틀린 토큰은 공개 경로에서도, 로그인 세션이 함께 있어도 401이다")
        void invalidTokenNeverFallsBackToSessionOrAnonymous() throws Exception {
            String unknown = "snt_" + "B".repeat(43);

            mockMvc.perform(withToken(HttpMethod.GET, "/api/v1/posts", unknown))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_001"));
            mockMvc.perform(
                            withToken(HttpMethod.GET, "/api/v1/notifications", unknown)
                                    .with(user(details(admin))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("API_TOKEN_001"));
            for (String malformed :
                    List.of(
                            "snt_",
                            "snt_" + "B".repeat(42),
                            "snt_" + "B".repeat(44),
                            "snt_" + "B".repeat(42) + "+",
                            "snt_" + "B".repeat(43) + " ")) {
                mockMvc.perform(
                                withToken(
                                        HttpMethod.GET,
                                        DRAFTS + "/" + UUID.randomUUID(),
                                        malformed))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("API_TOKEN_001"));
            }
        }

        @Test
        @DisplayName("오류 응답은 받은 토큰 값을 되돌려 주지 않는다")
        void errorDoesNotEchoToken() throws Exception {
            String unknown = "snt_" + "C".repeat(43);

            String response =
                    mockMvc.perform(
                                    withToken(
                                            HttpMethod.GET,
                                            DRAFTS + "/" + UUID.randomUUID(),
                                            unknown))
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString(StandardCharsets.UTF_8);

            assertThat(response).doesNotContain("C".repeat(43));
        }

        @Test
        @DisplayName("만료 시각이 지난 토큰과 소유자가 정지된 토큰은 401이다")
        void expiredOrSuspendedOwnerIsRejected() throws Exception {
            String expired = createToken(admin, "DRAFT");
            jdbc.update(
                    "UPDATE api_token SET expires_at = ? WHERE token_hash = ?",
                    Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)),
                    ApiTokenService.hash(expired));
            String suspended = createToken(otherAdmin, "DRAFT");
            jdbc.update(
                    "UPDATE member SET status = 'SUSPENDED' WHERE member_id = ?",
                    otherAdmin.getId());
            entityManager.clear();

            for (String token : List.of(expired, suspended)) {
                mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("API_TOKEN_001"));
            }
        }

        @Test
        @DisplayName("마지막 사용 시각은 처음 쓸 때 남고, 1분 안에 다시 써도 바뀌지 않는다")
        void lastUsedAtIsThrottled() throws Exception {
            String token = createToken(admin, "DRAFT");
            entityManager.flush();
            assertThat(lastUsedAt(token)).isNull();

            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isNotFound());
            Timestamp first = lastUsedAt(token);
            assertThat(first).isNotNull();

            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isNotFound());
            assertThat(lastUsedAt(token)).isEqualTo(first);

            // 1분이 지난 것으로 돌려 두면 다시 기록한다.
            Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusMinutes(2).withNano(0));
            jdbc.update(
                    "UPDATE api_token SET last_used_at = ? WHERE token_hash = ?",
                    old,
                    ApiTokenService.hash(token));
            entityManager.clear();
            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isNotFound());
            assertThat(lastUsedAt(token)).isAfter(old);
        }

        @Test
        @DisplayName("발급, 사용, 거부, 폐기 동안 평문 토큰이 로그에 남지 않는다")
        void plaintextIsNotLogged(CapturedOutput output) throws Exception {
            String token = createToken(admin, "READ", "DRAFT");
            String unknown = "snt_" + "D".repeat(43);

            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(withToken(HttpMethod.GET, "/api/v1/notifications", token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(withToken(HttpMethod.GET, DRAFTS + "/" + UUID.randomUUID(), unknown))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(
                            withToken(HttpMethod.POST, DRAFTS, token)
                                    .content("{\"body\":\"형식이 틀린 요청\"}"))
                    .andExpect(status().is4xxClientError());
            revoke(admin, tokenIdOf(token)).andExpect(status().isOk());

            assertThat(output.getAll())
                    .doesNotContain(token)
                    .doesNotContain(token.substring(12))
                    .doesNotContain(unknown);
        }
    }

    private record Route(HttpMethod method, String path) {}

    private Timestamp lastUsedAt(String plaintext) {
        return jdbc.queryForObject(
                "SELECT last_used_at FROM api_token WHERE token_hash = ?",
                Timestamp.class,
                ApiTokenService.hash(plaintext));
    }

    private int tokenCount(Member owner) {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_token WHERE member_id = ?", Integer.class, owner.getId());
    }

    private String tokenIdOf(String plaintext) {
        entityManager.flush();
        return jdbc.queryForObject(
                "SELECT public_id FROM api_token WHERE token_hash = ?",
                String.class,
                ApiTokenService.hash(plaintext));
    }

    private String createToken(Member owner, String... scopes) throws Exception {
        return json(create(owner, body("mcp-local", List.of(scopes), 7))
                        .andExpect(status().isCreated()))
                .get("token")
                .asText();
    }

    private ResultActions create(Member owner, Map<String, Object> request) throws Exception {
        return mockMvc.perform(
                post(TOKENS)
                        .with(csrf())
                        .with(user(details(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(request)));
    }

    private ResultActions revoke(Member owner, String tokenId) throws Exception {
        return mockMvc.perform(
                post(TOKENS + "/{id}/revoke", tokenId).with(csrf()).with(user(details(owner))));
    }

    private static Map<String, Object> body(String name, List<String> scopes, Integer days) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("scopes", scopes);
        request.put("expiresInDays", days);
        return request;
    }

    private static List<String> scopes(JsonNode token) {
        List<String> scopes = new ArrayList<>();
        token.get("scopes").forEach(scope -> scopes.add(scope.asText()));
        return scopes;
    }

    /** 토큰 요청은 세션 쿠키도 CSRF 토큰도 없이 보낸다. */
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            withToken(HttpMethod method, String path, String token) {
        return request(method, java.net.URI.create(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private CustomUserDetails details(Member member) {
        return new CustomUserDetails(member);
    }

    private String write(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Member saveMember(String unique, String suffix, Role role) {
        return members.save(
                Member.builder()
                        .publicId(UUID.randomUUID().toString())
                        .email(unique + "-" + suffix + "@example.com")
                        .nickname((suffix + unique).substring(0, 12))
                        .role(role)
                        .build());
    }
}
