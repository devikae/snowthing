package com.ikae.snowthing.domain.moderation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.moderation.dto.ModerationItem;
import com.ikae.snowthing.domain.moderation.service.ModerationAdminService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 자동 검열 검토 대기 (IKA-80). 권한은 SecurityConfig가 /api/v1/admin/ 아래 전체를 ROLE_ADMIN으로 막는다. */
@RestController
@RequestMapping("/api/v1/admin/moderations")
@RequiredArgsConstructor
public class AdminModerationController {

    private final ModerationAdminService service;

    @GetMapping
    public ResponseEntity<AdminPage<ModerationItem>> getQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getQueue(page, size));
    }

    /** 문제없음. 자동 숨김이면 풀고, 같은 내용은 다시 숨기지 않는다. */
    @PostMapping("/{id}/pass")
    public ResponseEntity<Void> pass(
            @PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        service.pass(id, admin);
        return ResponseEntity.noContent().build();
    }

    /** 숨김 유지. 검토 대기 항목이면 이때 숨긴다. */
    @PostMapping("/{id}/keep")
    public ResponseEntity<Void> keep(
            @PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        service.keep(id, admin);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        service.delete(id, admin);
        return ResponseEntity.noContent().build();
    }
}
