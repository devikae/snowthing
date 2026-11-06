package com.ikae.snowthing.domain.charactertest.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinRequest;
import com.ikae.snowthing.domain.charactertest.dto.CharacterJoinResponse;
import com.ikae.snowthing.domain.charactertest.entity.CharacterSport;
import com.ikae.snowthing.domain.charactertest.repository.CharacterGroupRepository;

@SpringBootTest
class CharacterGroupCleanupSchedulerTest {

    private static final String ANSWERS = "AAAAAAAAAAAAAAA";

    @Autowired CharacterGroupCleanupScheduler scheduler;
    @Autowired CharacterGroupService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired CharacterGroupRepository groupRepository;
    @Autowired TransactionTemplate transaction;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM character_participant");
        jdbc.update("DELETE FROM character_group");
    }

    @Test
    void 칠일_동안_새_참여가_없는_모임만_참여자와_함께_지운다() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 4, 20);
        CharacterJoinResponse expired = create("오래된모임");
        CharacterJoinResponse boundary = create("경계모임");
        CharacterJoinResponse fresh = create("새모임");
        service.join(
                expired.groupPublicId(),
                new CharacterJoinRequest("두번째", CharacterSport.BOARD, ANSWERS));
        setUpdatedAt(expired.groupPublicId(), now.minusDays(7).minusSeconds(1));
        setUpdatedAt(boundary.groupPublicId(), now.minusDays(7));
        setUpdatedAt(fresh.groupPublicId(), now.minusDays(1));

        assertThat(scheduler.deleteInactiveBefore(now)).isEqualTo(1);

        assertThat(countGroup(expired.groupPublicId())).isZero();
        assertThat(countGroup(boundary.groupPublicId())).isEqualTo(1);
        assertThat(countGroup(fresh.groupPublicId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM character_participant", Long.class))
                .isEqualTo(2L);
    }

    @Test
    void 새_참여가_있으면_보관_기간이_다시_시작된다() {
        LocalDateTime now = LocalDateTime.now();
        CharacterJoinResponse group = create("다시살아남");
        setUpdatedAt(group.groupPublicId(), now.minusDays(100));

        service.join(
                group.groupPublicId(),
                new CharacterJoinRequest("새친구", CharacterSport.SKI, ANSWERS));

        assertThat(scheduler.deleteInactiveBefore(now)).isZero();
        assertThat(countGroup(group.groupPublicId())).isEqualTo(1);
    }

    private CharacterJoinResponse create(String nickname) {
        return service.create(new CharacterJoinRequest(nickname, CharacterSport.SKI, ANSWERS));
    }

    // 조회와 같은 JPA 경로로 넣는다. SQL로 직접 넣으면 JVM 시간대에 따라 삭제 기준과 어긋난다.
    private void setUpdatedAt(String publicId, LocalDateTime value) {
        transaction.executeWithoutResult(
                status ->
                        groupRepository.touchJoinedAt(
                                groupRepository.findByPublicId(publicId).orElseThrow().getId(),
                                value));
    }

    private Long countGroup(String publicId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM character_group WHERE public_id = ?", Long.class, publicId);
    }
}
