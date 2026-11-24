package com.ikae.snowthing.domain.apitoken.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;

/** 토큰 한 개의 만료, 폐기, 범위 규칙 (IKA-118). 시각은 모두 고정 값이다. */
class ApiTokenTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 24, 9, 0);
    private static final LocalDateTime EXPIRES = CREATED.plusDays(7);

    @Test
    @DisplayName("만료 시각 직전까지만 쓸 수 있다. 만료 시각 정각부터는 못 쓴다")
    void expiryBoundary() {
        ApiToken token = issue(List.of(ApiTokenScope.READ));

        assertThat(token.isUsableAt(CREATED)).isTrue();
        assertThat(token.isUsableAt(EXPIRES.minusNanos(1))).isTrue();
        assertThat(token.isUsableAt(EXPIRES)).isFalse();
        assertThat(token.isUsableAt(EXPIRES.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("폐기하면 만료 전이라도 못 쓰고, 다시 폐기해도 처음 폐기 시각을 지킨다")
    void revokeKeepsFirstTime() {
        ApiToken token = issue(List.of(ApiTokenScope.DRAFT));
        LocalDateTime first = CREATED.plusHours(1);

        token.revoke(first);
        token.revoke(first.plusDays(1));

        assertThat(token.getRevokedAt()).isEqualTo(first);
        assertThat(token.isUsableAt(first)).isFalse();
        assertThat(token.isUsableAt(CREATED)).isFalse();
    }

    @Test
    @DisplayName("범위는 이름순으로 저장하고 같은 범위를 여러 번 줘도 하나다")
    void scopesAreSortedAndDistinct() {
        ApiToken token =
                issue(List.of(ApiTokenScope.READ, ApiTokenScope.DRAFT, ApiTokenScope.READ));

        assertThat(token.getScopes()).isEqualTo("DRAFT,READ");
        assertThat(token.scopeSet())
                .containsExactlyInAnyOrder(ApiTokenScope.READ, ApiTokenScope.DRAFT);
    }

    @Test
    @DisplayName("범위 권한 이름은 ROLE_ADMIN이나 봇 위임 권한과 겹치지 않는다")
    void scopeAuthoritiesAreDistinctFromRoles() {
        for (ApiTokenScope scope : ApiTokenScope.values()) {
            assertThat(scope.getAuthority())
                    .startsWith("API_TOKEN_")
                    .doesNotStartWith("ROLE_")
                    .doesNotStartWith("SCOPE_");
        }
        // 게시 범위는 일부러 두지 않는다. 승인과 게시는 관리자 세션으로만 한다.
        assertThat(ApiTokenScope.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder("READ", "DRAFT");
    }

    private static ApiToken issue(List<ApiTokenScope> scopes) {
        Member owner =
                Member.builder()
                        .email("token-owner@example.com")
                        .nickname("토큰주인")
                        .role(Role.ROLE_ADMIN)
                        .build();
        return ApiToken.issue(
                owner, "mcp-local", "snt_abcdefgh", "0".repeat(64), scopes, CREATED, EXPIRES);
    }
}
