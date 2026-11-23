package com.ikae.snowthing.domain.member.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기간이 끝난 회원 정지를 푼다 (IKA-115). 정지는 일 단위라 5분 늦게 풀려도 문제가 없다. 로그인 경로마다 만료를 따로 보지 않고 여기 한 곳에서 상태를 바꾼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberSuspensionExpiryScheduler {

    static final int BATCH_SIZE = 100;

    private final AdminMemberService adminMemberService;

    @Scheduled(
            cron = "${snowthing.member.suspension-expiry-cron:0 */5 * * * *}",
            zone = "Asia/Seoul")
    public void releaseExpired() {
        try {
            int released = adminMemberService.releaseExpiredSuspensions(BATCH_SIZE);
            if (released > 0) {
                log.info("회원 정지 기간 만료 해제 {}명", released);
            }
        } catch (RuntimeException e) {
            log.warn("회원 정지 만료 해제 실패: {}", e.getClass().getSimpleName(), e);
        }
    }
}
