package com.ikae.snowthing.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    @DisplayName("회원 생성 시 publicId(UUID), 기본 권한(ROLE_USER), 기본 상태(ACTIVE)가 자동 세팅되어야 한다")
    void createMember_DefaultValues_Success() {
        // given
        Member member =
                Member.builder()
                        .email("user@example.com")
                        .nickname("보더1")
                        .password("password123!")
                        .build();

        // when
        member.prePersist(); // @PrePersist 수동 호출 테스트

        // then
        assertThat(member.getPublicId()).isNotNull();
        assertThat(member.getPublicId()).hasSize(36); // UUID 36자 검증
        assertThat(member.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(member.getRole().getKey()).isEqualTo("ROLE_USER");
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("프로필 수정 시 닉네임, bio, 출발지 정보가 올바르게 업데이트되어야 한다")
    void updateProfile_Success() {
        // given
        Member member =
                Member.builder()
                        .email("user@example.com")
                        .nickname("보더1")
                        .bio("안녕하세요")
                        .departureRegion("서울")
                        .build();

        // when
        member.updateProfile("휘팍마스터", "카빙 연습 중", "경기 성남시", "https://image.url");

        // then
        assertThat(member.getNickname()).isEqualTo("휘팍마스터");
        assertThat(member.getBio()).isEqualTo("카빙 연습 중");
        assertThat(member.getDepartureRegion()).isEqualTo("경기 성남시");
        assertThat(member.getProfileImageUrl()).isEqualTo("https://image.url");
    }
}
