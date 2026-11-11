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

import com.ikae.snowthing.domain.admin.dto.AdminMemberDetailResponse;
import com.ikae.snowthing.domain.admin.service.AdminMemberDetailService;
import com.ikae.snowthing.domain.member.dto.AdminMemberListResponse;
import com.ikae.snowthing.domain.member.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.member.service.AdminMemberService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 회원 조회와 정지, 해제, 관리자 권한 지정과 해제. {@code /api/v1/admin/**}라 ROLE_ADMIN 세션만 들어온다. 상태 값을 받지 않고 동작별 경로만
 * 두어 탈퇴 등 다른 상태로는 바꿀 수 없다.
 */
@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final AdminMemberDetailService adminMemberDetailService;

    @GetMapping
    public ResponseEntity<AdminMemberListResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.search(query, page, size));
    }

    /** 회원 상세 (IKA-78). 이메일은 일부 가리고 비밀번호와 토큰은 내보내지 않는다. */
    @GetMapping("/{publicId}")
    public ResponseEntity<AdminMemberDetailResponse> detail(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberDetailService.get(admin, publicId));
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

    /** 관리자 권한 지정 (IKA-77). 봇 위임과 API 토큰은 ROLE_ADMIN이 없어 경로에서 막히지만 여기서도 거부한다. */
    @PostMapping("/{publicId}/grant-admin")
    public ResponseEntity<AdminMemberResponse> grantAdmin(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.grantAdmin(requireSessionAdmin(admin), publicId));
    }

    @PostMapping("/{publicId}/revoke-admin")
    public ResponseEntity<AdminMemberResponse> revokeAdmin(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminMemberService.revokeAdmin(requireSessionAdmin(admin), publicId));
    }

    private static String requireSessionAdmin(CustomUserDetails admin) {
        if (admin == null
                || admin instanceof BotDelegatedUserDetails
                || admin instanceof ApiTokenUserDetails) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        return admin.getPublicId();
    }
}
