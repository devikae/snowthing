package com.ikae.snowthing.domain.slopestatus.controller;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.slopestatus.dto.SlopeStatusBoardResponse;
import com.ikae.snowthing.domain.slopestatus.service.SlopeStatusQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/slope-status")
@RequiredArgsConstructor
public class SlopeStatusController {

    /** 수집은 2시간마다라 5분 캐시로 충분하다. 공개 정보라 공유 캐시도 허용한다. */
    private static final CacheControl BOARD_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(5))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(10));

    private final SlopeStatusQueryService queryService;

    /** 공개 API. 저장된 값만 읽고 외부 사이트를 부르지 않는다. */
    @GetMapping
    public ResponseEntity<SlopeStatusBoardResponse> getBoard() {
        return ResponseEntity.ok().cacheControl(BOARD_CACHE).body(queryService.getBoard());
    }
}
