package com.ikae.snowthing.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.*;
import com.ikae.snowthing.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.Optional;

@DataJpaTest
@Import(QuerydslConfig.class)
class MemberRepositoryCustomTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("findProfileByEmail - QueryDSL을 통해 회원 정보와 선호 스키장/스타일명을 DTO로 일괄 조회한다")
    void findProfileByEmail_Success() {
        // given
        Member member = memberRepository.save(Member.builder()
                .email("querydsl_profile@snowthing.com")
                .nickname("쿼리디에스엘")
                .password("encoded_pass")
                .bio("스노우보더")
                .build());

        Resort resort = entityManager.persist(Resort.builder().name("하이원").regionName("강원 정선").build());
        RidingStyle style = entityManager.persist(RidingStyle.builder().styleName("파크 / 기물 / 파이프").description("기물").build());

        entityManager.persist(MemberResort.builder().member(member).resort(resort).build());
        entityManager.persist(MemberRidingStyle.builder().member(member).ridingStyle(style).build());

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<MemberLoginResponse> profileOpt = memberRepository.findProfileByEmail("querydsl_profile@snowthing.com");

        // then
        assertThat(profileOpt).isPresent();
        MemberLoginResponse profile = profileOpt.get();
        assertThat(profile.getEmail()).isEqualTo("querydsl_profile@snowthing.com");
        assertThat(profile.getNickname()).isEqualTo("쿼리디에스엘");
        assertThat(profile.getResortNames()).containsExactly("하이원");
        assertThat(profile.getRidingStyleNames()).containsExactly("파크 / 기물 / 파이프");
    }
}
