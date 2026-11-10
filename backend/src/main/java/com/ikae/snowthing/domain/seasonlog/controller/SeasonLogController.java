package com.ikae.snowthing.domain.seasonlog.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.seasonlog.dto.SeasonLogResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonShareTokenResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitCreateRequest;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SharedSeasonResponse;
import com.ikae.snowthing.domain.seasonlog.service.SeasonLogService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SeasonLogController {

    private final SeasonLogService seasonLogService;

    @GetMapping("/season-logs/me")
    public ResponseEntity<SeasonLogResponse> getMySeason(
            @RequestParam(value = "season", required = false) Integer season,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(seasonLogService.getMySeason(memberId(userDetails), season));
    }

    @PostMapping("/season-logs/me/visits")
    public ResponseEntity<SeasonVisitResponse> addVisit(
            @Valid @RequestBody SeasonVisitCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(seasonLogService.addVisit(memberId(userDetails), request));
    }

    @DeleteMapping("/season-logs/me/visits/{visitDate}")
    public ResponseEntity<Void> deleteVisit(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate visitDate,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        seasonLogService.deleteVisit(memberId(userDetails), visitDate);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/season-logs/me/shares/{seasonStartYear}")
    public ResponseEntity<SeasonShareTokenResponse> enableShare(
            @PathVariable int seasonStartYear,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                seasonLogService.enableShare(memberId(userDetails), seasonStartYear));
    }

    @DeleteMapping("/season-logs/me/shares/{seasonStartYear}")
    public ResponseEntity<Void> disableShare(
            @PathVariable int seasonStartYear,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        seasonLogService.disableShare(memberId(userDetails), seasonStartYear);
        return ResponseEntity.noContent().build();
    }

    /** 공개 결산. 공유를 끄면 바로 404가 나야 하므로 캐시하지 않는다. */
    @GetMapping("/season-shares/{shareToken}")
    public ResponseEntity<SharedSeasonResponse> getSharedSeason(@PathVariable String shareToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(seasonLogService.getSharedSeason(shareToken));
    }

    private static Long memberId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }
        return userDetails.getMember().getId();
    }
}
