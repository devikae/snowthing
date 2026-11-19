package com.ikae.snowthing.domain.slope.controller;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.slope.dto.SlopeResponses.Compare;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortDetail;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortList;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.SlopeDetail;
import com.ikae.snowthing.domain.slope.service.SlopeQueryService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 슬로프 제원 비교 공개 API. 제원은 마이그레이션으로만 바뀌므로 공유 캐시를 허용한다. */
@RestController
@RequestMapping("/api/v1/slopes")
@RequiredArgsConstructor
public class SlopeController {

    private static final Pattern RESORT_CODE = Pattern.compile("[A-Z0-9_]{1,40}");

    private static final CacheControl SPEC_CACHE =
            CacheControl.maxAge(Duration.ofMinutes(5))
                    .cachePublic()
                    .staleWhileRevalidate(Duration.ofMinutes(30));

    private final SlopeQueryService queryService;

    @GetMapping("/resorts")
    public ResponseEntity<ResortList> getResorts() {
        return ResponseEntity.ok().cacheControl(SPEC_CACHE).body(queryService.getResorts());
    }

    @GetMapping("/resorts/{resortCode}")
    public ResponseEntity<ResortDetail> getResort(@PathVariable String resortCode) {
        return ResponseEntity.ok()
                .cacheControl(SPEC_CACHE)
                .body(queryService.getResort(requireCode(resortCode)));
    }

    @GetMapping("/{slopeId}")
    public ResponseEntity<SlopeDetail> getSlope(@PathVariable Long slopeId) {
        return ResponseEntity.ok().cacheControl(SPEC_CACHE).body(queryService.getSlope(slopeId));
    }

    /** codes는 쉼표로 구분한 리조트 코드 2~3개. */
    @GetMapping("/compare")
    public ResponseEntity<Compare> compare(@RequestParam String codes) {
        List<String> resortCodes =
                Arrays.stream(codes.split(","))
                        .map(String::trim)
                        .filter(c -> !c.isEmpty())
                        .toList();
        if (resortCodes.size() > SlopeQueryService.COMPARE_MAX) {
            throw new CustomException(ErrorCode.INVALID_SLOPE_COMPARE);
        }
        resortCodes.forEach(SlopeController::requireCode);
        return ResponseEntity.ok().cacheControl(SPEC_CACHE).body(queryService.compare(resortCodes));
    }

    private static String requireCode(String resortCode) {
        if (!RESORT_CODE.matcher(resortCode).matches()) {
            throw new CustomException(ErrorCode.RESORT_NOT_FOUND);
        }
        return resortCode;
    }
}
