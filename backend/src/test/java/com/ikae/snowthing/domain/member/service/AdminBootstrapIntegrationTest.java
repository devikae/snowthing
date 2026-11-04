package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

/** 첫 관리자 지정. 설정값만 바꿔 같은 컨텍스트에서 확인하려고 빈 대신 직접 만든다. */
@SpringBootTest
class AdminBootstrapIntegrationTest {

    @Autowired private NamedParameterJdbcTemplate namedJdbc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MemberRepository members;
    @Autowired private EmailNormalizer emailNormalizer;

    private final List<Long> memberIds = new ArrayList<>();
    private Member target;
    private Member withdrawn;
    private Member bystander;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        target = save("boot-" + unique + "@test.invalid", "bt" + unique, MemberStatus.ACTIVE);
        withdrawn = save("gone-" + unique + "@test.invalid", "bg" + unique, MemberStatus.WITHDRAWN);
        bystander = save("other-" + unique + "@test.invalid", "bo" + unique, MemberStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        memberIds.forEach(id -> jdbc.update("DELETE FROM member WHERE member_id = ?", id));
    }

    @Test
    @DisplayName("설정이 비어 있으면 아무도 바뀌지 않는다")
    void emptyConfigChangesNothing() {
        assertThat(bootstrap("").promote()).isZero();
        assertThat(bootstrap(" , ").promote()).isZero();
        assertThat(roleOf(target)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("목록의 활성 회원만 관리자가 되고 없는 이메일과 탈퇴 회원은 건너뛴다. 다시 실행해도 같다")
    void promotesOnlyListedActiveMembersIdempotently() {
        String config =
                " "
                        + target.getEmail().toUpperCase()
                        + " ,nobody-x@test.invalid,"
                        + withdrawn.getEmail();

        assertThat(bootstrap(config).promote()).isEqualTo(1);
        assertThat(roleOf(target)).isEqualTo("ROLE_ADMIN");
        assertThat(roleOf(withdrawn)).isEqualTo("ROLE_USER");
        assertThat(roleOf(bystander)).isEqualTo("ROLE_USER");

        assertThat(bootstrap(config).promote()).isZero();
        assertThat(roleOf(target)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("목록에서 빠져도 관리자 권한을 거두지 않는다")
    void removingEmailDoesNotDemote() {
        bootstrap(target.getEmail()).promote();
        assertThat(bootstrap(bystander.getEmail()).promote()).isEqualTo(1);
        assertThat(roleOf(target)).isEqualTo("ROLE_ADMIN");
    }

    private AdminBootstrap bootstrap(String emails) {
        return new AdminBootstrap(namedJdbc, emailNormalizer, emails);
    }

    private String roleOf(Member member) {
        return jdbc.queryForObject(
                "SELECT role FROM member WHERE member_id = ?", String.class, member.getId());
    }

    private Member save(String email, String nickname, MemberStatus status) {
        Member member =
                members.save(
                        Member.builder()
                                .publicId(UUID.randomUUID().toString())
                                .email(email)
                                .nickname(nickname)
                                .role(Role.ROLE_USER)
                                .status(status)
                                .build());
        memberIds.add(member.getId());
        return member;
    }
}
