package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminCommentItem;
import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.dto.AdminPostBlindRequest;
import com.ikae.snowthing.domain.admin.service.AdminCommentService;
import com.ikae.snowthing.domain.admin.service.AdminQueryService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 관리자 댓글 관리 (IKA-78). */
@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final AdminQueryService adminQueryService;
    private final AdminCommentService adminCommentService;

    @GetMapping
    public ResponseEntity<AdminPage<AdminCommentItem>> list(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminQueryService.comments(admin, filter, query, author, page, size));
    }

    @PatchMapping("/{commentId}/blind")
    public ResponseEntity<Void> setBlinded(
            @PathVariable Long commentId,
            @RequestBody AdminPostBlindRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminCommentService.setBlinded(admin, commentId, request.blinded());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails admin) {
        adminCommentService.delete(admin, commentId);
        return ResponseEntity.noContent().build();
    }
}
