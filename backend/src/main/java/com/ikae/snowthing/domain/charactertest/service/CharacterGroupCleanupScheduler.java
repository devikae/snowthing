package com.ikae.snowthing.domain.charactertest.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.charactertest.repository.CharacterGroupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 매일 04:20 KST에 30일 동안 새 참여가 없는 모임을 참여자와 함께 지운다. 배치마다 따로 커밋해 긴 락을 피한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CharacterGroupCleanupScheduler {

    static final int RETENTION_DAYS = 30;
    static final int BATCH_SIZE = 200;
    static final int MAX_BATCHES = 200;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CharacterGroupRepository groupRepository;
    private final CharacterGroupCleanupService cleanupService;

    @Scheduled(cron = "${snowthing.character-test.cleanup-cron:0 20 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        try {
            int deleted = deleteInactiveBefore(LocalDateTime.now(KST));
            log.info("오래된 캐릭터 테스트 모임 삭제 {}건", deleted);
        } catch (RuntimeException e) {
            log.warn("오래된 캐릭터 테스트 모임 삭제 실패: {}", e.getClass().getSimpleName(), e);
        }
    }

    int deleteInactiveBefore(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RETENTION_DAYS);
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            List<Long> ids = groupRepository.findExpiredIds(cutoff, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            total += cleanupService.deleteExpired(ids, cutoff);
            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
