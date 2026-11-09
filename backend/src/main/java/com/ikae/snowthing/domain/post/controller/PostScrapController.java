package com.ikae.snowthing.domain.post.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.post.dto.ScrapPostResponse;
import com.ikae.snowthing.domain.post.dto.ScrapResponse;
import com.ikae.snowthing.domain.post.service.PostScrapService;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 글 스크랩. 설계: docs/conception/026_post_scrap/01_design.md
 *
 * <p>{@code /api/v1/posts/**}는 비회원도 쓰는 경로라 SecurityConfig가 열어 두므로 로그인 확인은 서비스가 한다. 내 스크랩 목록은
 * SecurityConfig의 나머지 규칙(authenticated)에도 걸린다.
 */
@RestController
@RequiredArgsConstructor
public class PostScrapController {

    private final PostScrapService postScrapService;

    @PutMapping("/api/v1/posts/{publicId}/scrap")
    public ResponseEntity<ScrapResponse> scrap(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(postScrapService.scrap(publicId, userDetails));
    }

    @DeleteMapping("/api/v1/posts/{publicId}/scrap")
    public ResponseEntity<ScrapResponse> unscrap(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(postScrapService.unscrap(publicId, userDetails));
    }

    @GetMapping("/api/v1/members/me/scraps")
    public ResponseEntity<CursorPageResponse<ScrapPostResponse>> getMyScraps(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(postScrapService.getMyScraps(userDetails, cursor, size));
    }
}
