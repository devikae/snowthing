package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.admin.dto.AdminMemberDetailResponse;
import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.apitoken.entity.ApiTokenScope;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 관리자 서비스 공통 규칙 단위 테스트 (IKA-78). */
class AdminAccessTest {

    @Test
    @DisplayName("세션 관리자만 통과하고 일반 회원, 봇 위임, API 토큰, 비로그인은 거부한다")
    void requiresSessionAdmin() {
        Member admin = member(Role.ROLE_ADMIN);
        assertThatCode(() -> AdminAccess.require(new CustomUserDetails(admin)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AdminAccess.require(new CustomUserDetails(member(Role.ROLE_USER))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> AdminAccess.require(new BotDelegatedUserDetails(admin)))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(
                        () ->
                                AdminAccess.require(
                                        new ApiTokenUserDetails(
                                                admin, 1L, Set.of(ApiTokenScope.READ))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> AdminAccess.require(null)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("쪽 크기는 1에서 50까지이고 검색어의 LIKE 특수문자는 글자로 찾는다")
    void pagingAndPattern() {
        assertThat(AdminPaging.of(null, null)).isEqualTo(new AdminPaging(0, 20));
        assertThatThrownBy(() -> AdminPaging.of(0, 51)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> AdminPaging.of(-1, 10)).isInstanceOf(CustomException.class);
        assertThat(AdminPaging.containsPattern("  ")).isNull();
        assertThat(AdminPaging.containsPattern(" 50%_! ")).isEqualTo("%50!%!_!!%");
        assertThatThrownBy(() -> AdminPaging.containsPattern("a".repeat(101)))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("한 건 더 읽었으면 다음 쪽이 있고 응답에서는 뺀다")
    void pageHasNext() {
        AdminPage<Integer> page = AdminPage.of(java.util.List.of(1, 2, 3), 0, 2);
        assertThat(page.items()).containsExactly(1, 2);
        assertThat(page.hasNext()).isTrue();
        assertThat(AdminPage.of(java.util.List.of(1), 0, 2).hasNext()).isFalse();
    }

    @Test
    @DisplayName("이메일은 앞 두 글자와 도메인만 보인다")
    void masksEmail() {
        assertThat(AdminMemberDetailResponse.maskEmail("rider@snowthing.org"))
                .isEqualTo("ri***@snowthing.org");
        assertThat(AdminMemberDetailResponse.maskEmail("ab@x.com")).isEqualTo("a***@x.com");
        assertThat(AdminMemberDetailResponse.maskEmail("broken")).isEqualTo("***");
    }

    private static Member member(Role role) {
        return Member.builder()
                .publicId("p-" + role)
                .email("m@x.com")
                .nickname("m")
                .role(role)
                .build();
    }
}
