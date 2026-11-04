package com.ikae.snowthing.domain.member.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.member.dto.BlockedMemberResponse;
import com.ikae.snowthing.domain.member.service.MemberBlockService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 회원 차단. 모두 로그인이 필요하다(SecurityConfig의 나머지 경로 authenticated). */
@RestController
@RequestMapping("/api/v1/members/blocks")
@RequiredArgsConstructor
public class MemberBlockController {

    private final MemberBlockService memberBlockService;

    @GetMapping
    public ResponseEntity<List<BlockedMemberResponse>> getMyBlocks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(memberBlockService.getMyBlocks(userDetails));
    }

    @PostMapping("/{memberPublicId}")
    public ResponseEntity<Void> block(
            @PathVariable String memberPublicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberBlockService.block(userDetails, memberPublicId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{memberPublicId}")
    public ResponseEntity<Void> unblock(
            @PathVariable String memberPublicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        memberBlockService.unblock(userDetails, memberPublicId);
        return ResponseEntity.noContent().build();
    }
}
