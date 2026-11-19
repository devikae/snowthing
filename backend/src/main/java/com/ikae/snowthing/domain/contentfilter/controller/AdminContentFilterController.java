package com.ikae.snowthing.domain.contentfilter.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.contentfilter.dto.BannedWordCreateRequest;
import com.ikae.snowthing.domain.contentfilter.dto.BannedWordResponse;
import com.ikae.snowthing.domain.contentfilter.dto.FilterHoldItem;
import com.ikae.snowthing.domain.contentfilter.service.BannedWordService;
import com.ikae.snowthing.domain.contentfilter.service.FilterHoldAdminService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 금칙어와 보류 검토 (IKA-96). 권한은 SecurityConfig가 /api/v1/admin/ 아래 전체를 ROLE_ADMIN으로 막는다. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminContentFilterController {

    private final BannedWordService bannedWordService;
    private final FilterHoldAdminService filterHoldAdminService;

    @GetMapping("/banned-words")
    public ResponseEntity<List<BannedWordResponse>> getBannedWords() {
        return ResponseEntity.ok(bannedWordService.getWords());
    }

    @PostMapping("/banned-words")
    public ResponseEntity<BannedWordResponse> createBannedWord(
            @Valid @RequestBody BannedWordCreateRequest request) {
        return ResponseEntity.ok(bannedWordService.create(request.word()));
    }

    @DeleteMapping("/banned-words/{id}")
    public ResponseEntity<Void> deleteBannedWord(@PathVariable("id") Long id) {
        bannedWordService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/filter-holds")
    public ResponseEntity<AdminPage<FilterHoldItem>> getHolds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(filterHoldAdminService.getHolds(page, size));
    }

    @PostMapping("/filter-holds/{id}/release")
    public ResponseEntity<Void> release(@PathVariable("id") Long id) {
        filterHoldAdminService.release(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/filter-holds/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        filterHoldAdminService.delete(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/filter-holds/{id}/ban-ip")
    public ResponseEntity<ChatBannedIpResponse> banWriterRange(
            @PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails admin) {
        String createdBy =
                admin != null && admin.getUsername() != null ? admin.getUsername() : "ADMIN";
        return ResponseEntity.ok(filterHoldAdminService.banWriterRange(id, createdBy));
    }
}
