package com.ikae.snowthing.domain.dmmail.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.dmmail.dto.DmMailOutboxSummaryResponse;
import com.ikae.snowthing.domain.dmmail.entity.DmMailStatus;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;

import lombok.RequiredArgsConstructor;

/**
 * 쪽지 메일 발송 상태 조회 (IKA-88). 검색 outbox 요약({@code /api/v1/admin/search/outbox/summary})과 같은 방식이다.
 * {@code /api/v1/admin/**}는 SecurityConfig에서 ROLE_ADMIN만 허용한다. 읽기만 하고 상태를 바꾸는 API는 두지 않는다.
 */
@RestController
@RequestMapping("/api/v1/admin/dm-mail")
@RequiredArgsConstructor
public class DmMailAdminController {

    private final DmMailOutboxRepository outbox;
    private final Clock clock;

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
