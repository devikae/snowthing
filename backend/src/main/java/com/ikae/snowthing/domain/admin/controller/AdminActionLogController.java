package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminActionLogItem;
import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.service.AdminQueryService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 관리자 조치 기록 (IKA-78). */
@RestController
@RequestMapping("/api/v1/admin/action-logs")
@RequiredArgsConstructor
public class AdminActionLogController {

    private final AdminQueryService adminQueryService;

    @GetMapping
    public ResponseEntity<AdminPage<AdminActionLogItem>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        adminQueryService.actionLogs(
                                admin, action, targetType, targetId, actor, page, size));
    }
}
