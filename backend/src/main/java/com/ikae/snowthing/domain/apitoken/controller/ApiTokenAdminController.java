package com.ikae.snowthing.domain.apitoken.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.apitoken.dto.ApiTokenCreateRequest;
import com.ikae.snowthing.domain.apitoken.dto.ApiTokenCreateResponse;
import com.ikae.snowthing.domain.apitoken.dto.ApiTokenResponse;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 본인 API 토큰 관리. {@code /api/v1/admin/**}라 ROLE_ADMIN 세션만 들어온다. API 토큰 인증에는 ROLE_ADMIN 권한이 없으므로 토큰으로
 * 토큰을 만들 수 없다.
 */
@RestController
@RequestMapping("/api/v1/admin/api-tokens")
@RequiredArgsConstructor
public class ApiTokenAdminController {

    private final ApiTokenService apiTokenService;

    @PostMapping
    public ResponseEntity<ApiTokenCreateResponse> create(
            @RequestBody(required = false) ApiTokenCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(apiTokenService.create(userDetails.getMember().getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<ApiTokenResponse>> list(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(apiTokenService.list(userDetails.getMember().getId()));
    }

    @PostMapping("/{tokenId}/revoke")
    public ResponseEntity<ApiTokenResponse> revoke(
            @PathVariable String tokenId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(apiTokenService.revoke(userDetails.getMember().getId(), tokenId));
    }
}
