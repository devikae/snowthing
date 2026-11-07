package com.ikae.snowthing.domain.member.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.member.dto.AdminMemberListResponse;
import com.ikae.snowthing.domain.member.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.member.service.AdminMemberService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 회원 조회와 정지, 해제. {@code /api/v1/admin/**}라 ROLE_ADMIN 세션만 들어온다. 상태 값을 받지 않고 동작별 경로만 두어 탈퇴 등 다른 상태로는
 * 바꿀 수 없다.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<AdminMemberListResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.search(query, page, size));
    }

    @PostMapping("/{publicId}/suspend")
    public ResponseEntity<AdminMemberResponse> suspend(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.suspend(admin.getPublicId(), publicId));
    }

    @PostMapping("/{publicId}/reinstate")
    public ResponseEntity<AdminMemberResponse> reinstate(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.reinstate(admin.getPublicId(), publicId));
    }
}
