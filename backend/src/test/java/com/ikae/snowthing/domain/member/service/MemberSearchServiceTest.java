package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.MemberSearchResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 앞글자 일치, 제외 대상, LIKE 특수문자, 결과 개수 제한을 실제 쿼리로 확인한다. */
@SpringBootTest
@Transactional
class MemberSearchServiceTest {

    @Autowired private MemberSearchService search;
    @Autowired private MemberRepository memberRepository;

    private CustomUserDetails viewer;

    @BeforeEach
    void setUp() {
        viewer = new CustomUserDetails(save("zsearch-viewer", MemberStatus.ACTIVE));
    }

    private Member save(String nickname, MemberStatus status) {
        return memberRepository.saveAndFlush(
                Member.builder()
                        .email(nickname + "@example.com")
                        .nickname(nickname)
                        .role(Role.ROLE_USER)
                        .status(status)
                        .build());
    }

    private List<String> nicknames(String keyword) {
        return search.search(viewer, keyword).stream().map(MemberSearchResponse::nickname).toList();
    }

    @Test
    @DisplayName("앞글자가 같은 회원만 닉네임 순으로 찾고 중간 글자로는 찾지 않는다")
    void matchesPrefixOnly() {
        save("zsearch-b", MemberStatus.ACTIVE);
        save("zsearch", MemberStatus.ACTIVE);
        save("xx-zsearch", MemberStatus.ACTIVE);

        assertThat(nicknames("zsearch-b")).containsExactly("zsearch-b");
        assertThat(nicknames("zsearch")).containsExactly("zsearch", "zsearch-b");
    }

    @Test
    @DisplayName("본인과 비활성 회원은 결과에 없다")
    void excludesViewerAndInactive() {
        save("zsearch-withdrawn", MemberStatus.WITHDRAWN);

        assertThat(nicknames("zsearch")).isEmpty();
    }

    @Test
    @DisplayName("AI 회원도 일반 회원처럼 검색된다")
    void includesAiMembers() {
        memberRepository.saveAndFlush(
                Member.builder()
                        .email("zsearch-ai@snowthing.invalid")
                        .nickname("zsearch-ai")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build());

        assertThat(nicknames("zsearch")).containsExactly("zsearch-ai");
    }

    @Test
    @DisplayName("LIKE 특수문자는 글자 그대로 찾는다")
    void escapesLikeCharacters() {
        save("zsearch_under", MemberStatus.ACTIVE);
        save("zsearchXunder", MemberStatus.ACTIVE);

        assertThat(nicknames("%%")).isEmpty();
        assertThat(nicknames("zsearch_")).containsExactly("zsearch_under");
    }

    @Test
    @DisplayName("2글자 미만은 조회하지 않고 결과는 최대 10명이다")
    void limitsLengthAndResults() {
        for (int index = 0; index < 12; index++) save("zsearch" + index + "x", MemberStatus.ACTIVE);

        assertThat(nicknames("z")).isEmpty();
        assertThat(nicknames(" ")).isEmpty();
        assertThat(nicknames("zsearch")).hasSize(10);
    }
}
