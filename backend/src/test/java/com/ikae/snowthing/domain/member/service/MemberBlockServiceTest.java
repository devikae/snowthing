package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.BlockedMemberResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 차단은 UNIQUE 위반 500 없이 멱등하고, 목록은 최근 차단 순이다. */
@SpringBootTest
@Transactional
class MemberBlockServiceTest {

    @Autowired private MemberBlockService blocks;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbc;

    private CustomUserDetails alice;
    private Member bob;
    private Member carol;

    @BeforeEach
    void setUp() {
        alice = new CustomUserDetails(saveMember("block-alice", MemberStatus.ACTIVE));
        bob = saveMember("block-bob", MemberStatus.ACTIVE);
        carol = saveMember("block-carol", MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("같은 회원을 두 번 차단해도 예외 없이 한 줄만 남는다")
    void duplicateBlockIsIdempotent() {
        blocks.block(alice, bob.getPublicId());
        assertThatCode(() -> blocks.block(alice, bob.getPublicId())).doesNotThrowAnyException();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM member_block WHERE blocker_id = ? AND blocked_id = ?",
                                Long.class,
                                alice.getMember().getId(),
                                bob.getId()))
                .isEqualTo(1L);
        assertThat(blocks.isBlocked(alice.getMember().getId(), bob.getId())).isTrue();
        assertThat(blocks.isBlocked(bob.getId(), alice.getMember().getId())).isFalse();
    }

    @Test
    @DisplayName("자기 자신은 차단할 수 없고 없는 회원은 404다")
    void invalidTargets() {
        assertThatThrownBy(() -> blocks.block(alice, alice.getPublicId()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_BLOCK_SELF_NOT_ALLOWED);
        assertThatThrownBy(() -> blocks.block(alice, UUID.randomUUID().toString()))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("차단 해제는 차단하지 않은 회원이어도 성공하고, 목록은 최근 차단 순이다")
    void unblockAndList() {
        blocks.block(alice, bob.getPublicId());
        blocks.block(alice, carol.getPublicId());

        assertThat(blocks.getMyBlocks(alice))
                .extracting(BlockedMemberResponse::publicId)
                .containsExactly(carol.getPublicId(), bob.getPublicId());

        blocks.unblock(alice, bob.getPublicId());
        assertThatCode(() -> blocks.unblock(alice, bob.getPublicId())).doesNotThrowAnyException();
        assertThat(blocks.getMyBlocks(alice))
                .extracting(BlockedMemberResponse::publicId)
                .containsExactly(carol.getPublicId());
    }

    @Test
    @DisplayName("탈퇴한 회원은 목록에서 닉네임을 가리지만 해제할 수 있다")
    void withdrawnInList() {
        Member withdrawn = saveMember("block-gone", MemberStatus.WITHDRAWN);
        blocks.block(alice, withdrawn.getPublicId());

        BlockedMemberResponse row = blocks.getMyBlocks(alice).get(0);
        assertThat(row.withdrawn()).isTrue();
        assertThat(row.nickname()).isEqualTo("탈퇴한 회원");
        assertThat(row.publicId()).isEqualTo(withdrawn.getPublicId());
        assertThat(row.blockedAt()).isNotNull();
    }

    private Member saveMember(String prefix, MemberStatus status) {
        String unique = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return memberRepository.save(
                Member.builder()
                        .email(unique + "@example.com")
                        .nickname(unique)
                        .role(Role.ROLE_USER)
                        .status(status)
                        .build());
    }
}
