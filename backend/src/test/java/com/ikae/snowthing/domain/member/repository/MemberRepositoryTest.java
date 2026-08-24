package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ikae.snowthing.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class MemberRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("DB 저장 시 UUID public_id, Role(STRING), MemberStatus(STRING)가 정상 저장되어야 한다")
    void saveMember_JpaAndEnums_Success() {
        // given
        Member member = Member.builder()
                .email("test@snowthing.com")
                .nickname("휘팍러버")
                .password("encoded_password")
                .bio("스노보드 타는 중")
                .departureRegion("서울 송파구")
                .build();

        // when
        Member savedMember = entityManager.persistAndFlush(member);
        entityManager.clear();

        // then
        Member foundMember = entityManager.find(Member.class, savedMember.getId());

        assertThat(foundMember).isNotNull();
        assertThat(foundMember.getId()).isNotNull();
        assertThat(foundMember.getPublicId()).isNotNull();
        assertThat(foundMember.getPublicId()).hasSize(36);
        assertThat(foundMember.getEmail()).isEqualTo("test@snowthing.com");
        assertThat(foundMember.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(foundMember.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
