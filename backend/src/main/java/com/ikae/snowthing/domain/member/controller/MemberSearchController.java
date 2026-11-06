package com.ikae.snowthing.domain.member.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.member.dto.MemberSearchResponse;
import com.ikae.snowthing.domain.member.service.MemberSearchService;
import com.ikae.snowthing.domain.member.service.ProfileActionRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 회원 닉네임 검색. 로그인이 필요하다(SecurityConfig의 나머지 경로 authenticated). */
@RestController
@RequestMapping("/api/v1/members/search")
@RequiredArgsConstructor
public class MemberSearchController {

    private final MemberSearchService memberSearchService;
    private final ProfileActionRateLimiter rateLimiter;

    @GetMapping
    public ResponseEntity<List<MemberSearchResponse>> search(
            @RequestParam(defaultValue = "") String nickname,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        rateLimiter.check(userDetails.getUsername(), ProfileActionRateLimiter.Action.MEMBER_SEARCH);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(memberSearchService.search(userDetails, nickname));
    }
}
