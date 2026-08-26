package com.ikae.snowthing.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.*;

@SpringBootTest
@Transactional
class MemberRepositoryCustomTest {

    @Autowired private MemberRepository memberRepository;

    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("findProfileByEmail - QueryDSL을 통해 회원 정보와 선호 스키장/스타일명을 DTO로 일괄 조회한다")
    void findProfileByEmail_Success() {
        // given
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("querydsl_profile@snowthing.com")
                                .nickname("쿼리디에스엘")
                                .password("encoded_pass")
                                .bio("스노우보더")
                                .build());

        Resort resort = Resort.builder().name("테스트하이원").regionName("강원 정선").build();
        entityManager.persist(resort);
        RidingStyle style = RidingStyle.builder().styleName("테스트파크").description("기물").build();
        entityManager.persist(style);

        entityManager.persist(MemberResort.builder().member(member).resort(resort).build());
        entityManager.persist(
                MemberRidingStyle.builder().member(member).ridingStyle(style).build());

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<MemberLoginResponse> profileOpt =
                memberRepository.findProfileByEmail("querydsl_profile@snowthing.com");

        // then
        assertThat(profileOpt).isPresent();
        MemberLoginResponse profile = profileOpt.get();
        assertThat(profile.getEmail()).isEqualTo("querydsl_profile@snowthing.com");
        assertThat(profile.getNickname()).isEqualTo("쿼리디에스엘");
        assertThat(profile.getResortNames()).containsExactly("테스트하이원");
        assertThat(profile.getRidingStyleNames()).containsExactly("테스트파크");
    }

    @Test
    @DisplayName(
            "findProfileByEmail - 회원이 여러 개의 리조트와 라이딩 스타일을 보유했을 때 Cartesian Product 없이 정확히 조회된다")
    void findProfileByEmail_multipleCollections_success() {
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("multi_collection@snowthing.com")
                                .nickname("다중선호보더")
                                .password("encoded_pass")
                                .bio("올라운더")
                                .build());

        Resort resort1 = Resort.builder().name("테스트용평").regionName("강원 평창").build();
        entityManager.persist(resort1);
        Resort resort2 = Resort.builder().name("테스트휘닉스").regionName("강원 평창").build();
        entityManager.persist(resort2);

        RidingStyle style1 = RidingStyle.builder().styleName("테스트라이딩").description("카빙").build();
        entityManager.persist(style1);
        RidingStyle style2 = RidingStyle.builder().styleName("테스트그라운드트릭").description("트릭").build();
        entityManager.persist(style2);

        entityManager.persist(MemberResort.builder().member(member).resort(resort1).build());
        entityManager.persist(MemberResort.builder().member(member).resort(resort2).build());

        entityManager.persist(
                MemberRidingStyle.builder().member(member).ridingStyle(style1).build());
        entityManager.persist(
                MemberRidingStyle.builder().member(member).ridingStyle(style2).build());

        entityManager.flush();
        entityManager.clear();

        Optional<MemberLoginResponse> profileOpt =
                memberRepository.findProfileByEmail("multi_collection@snowthing.com");

        assertThat(profileOpt).isPresent();
        MemberLoginResponse profile = profileOpt.get();
        assertThat(profile.getResortNames()).containsExactlyInAnyOrder("테스트용평", "테스트휘닉스");
        assertThat(profile.getRidingStyleNames()).containsExactlyInAnyOrder("테스트라이딩", "테스트그라운드트릭");
    }
}
