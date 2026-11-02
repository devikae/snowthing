package com.ikae.snowthing.domain.admin.controller;

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
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminChatMessageBanRequest;
import com.ikae.snowthing.domain.admin.dto.ChatBannedIpCreateRequest;
import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/chat")
@RequiredArgsConstructor
public class AdminChatController {

    private final ChatIpBanService chatIpBanService;

    @GetMapping("/banned-ips")
    public ResponseEntity<List<ChatBannedIpResponse>> getBannedIps() {
        List<ChatBannedIpResponse> bannedIps = chatIpBanService.getBannedIps();
        return ResponseEntity.ok(bannedIps);
    }

    @PostMapping("/banned-ips")
    public ResponseEntity<ChatBannedIpResponse> banIp(
            @Valid @RequestBody ChatBannedIpCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String createdBy = (userDetails != null && userDetails.getUsername() != null)
                ? userDetails.getUsername()
                : "ADMIN";
        ChatBannedIpResponse response = chatIpBanService.banIp(
                request.bannedIp(), request.reason(), request.bannedUntil(), createdBy);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/banned-ips/{id}")
    public ResponseEntity<Void> unbanIp(@PathVariable("id") Long id) {
        chatIpBanService.unbanIp(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages/{messageId}/ban-ip")
    public ResponseEntity<Void> banByMessageId(
            @PathVariable("messageId") String messageId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String createdBy = (userDetails != null && userDetails.getUsername() != null)
                ? userDetails.getUsername()
                : "ADMIN";
        chatIpBanService.banIpByMessageId(messageId, createdBy);
        return ResponseEntity.noContent().build();
    }
}
