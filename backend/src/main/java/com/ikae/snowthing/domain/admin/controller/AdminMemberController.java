package com.ikae.snowthing.domain.admin.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminMemberNicknameUpdateRequest;
import com.ikae.snowthing.domain.admin.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.admin.dto.AdminMemberStatusUpdateRequest;
import com.ikae.snowthing.domain.admin.service.AdminMemberService;
import com.ikae.snowthing.domain.member.entity.MemberStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping
    public ResponseEntity<Page<AdminMemberResponse>> searchMembers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) MemberStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        Page<AdminMemberResponse> members = adminMemberService.getMembers(query, status, pageable);
        return ResponseEntity.ok(members);
    }

    @PatchMapping("/{publicId}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody AdminMemberStatusUpdateRequest request) {
        adminMemberService.updateStatus(publicId, request.status());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{publicId}/nickname")
    public ResponseEntity<Void> updateNickname(
            @PathVariable("publicId") String publicId,
            @Valid @RequestBody AdminMemberNicknameUpdateRequest request) {
        adminMemberService.updateNickname(publicId, request.nickname());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{publicId}/profile-image")
    public ResponseEntity<Void> deleteProfileImage(@PathVariable("publicId") String publicId) {
        adminMemberService.resetProfileImage(publicId);
        return ResponseEntity.noContent().build();
    }
}
