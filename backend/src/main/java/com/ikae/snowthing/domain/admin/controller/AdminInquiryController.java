package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminInquiryItem;
import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.service.AdminQueryService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 1:1 문의 목록 (IKA-78). 답변은 기존처럼 문의 글 상세에서 관리자 댓글로 단다. */
@RestController
@RequestMapping("/api/v1/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminQueryService adminQueryService;

    @GetMapping
    public ResponseEntity<AdminPage<AdminInquiryItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminQueryService.inquiries(admin, status, page, size));
    }
}
