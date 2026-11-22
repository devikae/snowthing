package com.ikae.snowthing.domain.dmmail.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.dto.DmMailOutboxSummaryResponse;
import com.ikae.snowthing.domain.dmmail.dto.DmMailSwitchesResponse;
import com.ikae.snowthing.domain.dmmail.dto.DmMailSwitchesResponse.SwitchState;
import com.ikae.snowthing.domain.dmmail.entity.DmMailStatus;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;
import com.ikae.snowthing.domain.dmmail.service.DmMailSwitches;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 메일 발송 상태 조회 (IKA-88). 검색 outbox 요약({@code /api/v1/admin/search/outbox/summary})과 같은 방식이다.
 * {@code /api/v1/admin/**}는 SecurityConfig에서 ROLE_ADMIN만 허용한다. 읽기만 한다. 스위치 값은 관리자 설정 API({@code
 * PATCH /api/v1/admin/configs/{key}})로 바꾸고, 발송 요청 행의 상태를 바꾸는 API는 두지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/dm-mail")
@RequiredArgsConstructor
public class DmMailAdminController {

    private final DmMailOutboxRepository outbox;
    private final Clock clock;
    private final DmMailSwitches switches;
    private final DmMailProperties properties;

    /** 기록, 발송 스위치와 서버 설정 차단 여부 (IKA-109). */
    @GetMapping("/switches")
    public ResponseEntity<DmMailSwitchesResponse> switches() {
        DmMailSwitches.Snapshot now = switches.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        new DmMailSwitchesResponse(
                                new SwitchState(
                                        SystemConfigService.KEY_DM_MAIL_ENQUEUE_ENABLED,
                                        now.enqueueServerAllowed(),
                                        now.enqueueSwitchOn(),
                                        now.enqueueEffective()),
                                new SwitchState(
                                        SystemConfigService.KEY_DM_MAIL_SEND_ENABLED,
                                        now.sendServerAllowed(),
                                        now.sendSwitchOn(),
                                        now.sendEffective()),
                                now.mailProviderReady(),
                                properties.deadline().toHours()));
    }

    @GetMapping("/outbox/summary")
    public ResponseEntity<DmMailOutboxSummaryResponse> summary() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<DmMailStatus, Long> counts = outbox.countByStatus();
        LocalDateTime oldest = outbox.findOldestOpenCreatedAt();
        return ResponseEntity.ok(
                new DmMailOutboxSummaryResponse(
                        counts,
                        oldest,
                        oldest == null
                                ? null
                                : Math.max(0, Duration.between(oldest, now).toSeconds()),
                        counts.get(DmMailStatus.UNKNOWN) + counts.get(DmMailStatus.FAILED),
                        now));
    }
}
