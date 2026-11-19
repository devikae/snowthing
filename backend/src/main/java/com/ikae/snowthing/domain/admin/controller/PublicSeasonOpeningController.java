package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.SeasonOpeningResponse;
import com.ikae.snowthing.domain.admin.service.SeasonOpeningService;

import lombok.RequiredArgsConstructor;

/** 리조트 개장일 공개 조회 (IKA-87). 관리자가 바꾼 날짜가 새로고침에 바로 보이도록 브라우저가 매번 다시 묻게 한다(no-cache). */
@RestController
@RequestMapping("/api/v1/public/season-openings")
@RequiredArgsConstructor
public class PublicSeasonOpeningController {

    private final SeasonOpeningService seasonOpeningService;

    @GetMapping
    public ResponseEntity<SeasonOpeningResponse> getSeasonOpenings() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(seasonOpeningService.getSeasonOpenings());
    }
}
