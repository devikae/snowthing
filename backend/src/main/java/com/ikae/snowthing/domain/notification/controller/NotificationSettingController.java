package com.ikae.snowthing.domain.notification.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.notification.dto.NotificationSettingResponse;
import com.ikae.snowthing.domain.notification.dto.NotificationSettingUpdateRequest;
import com.ikae.snowthing.domain.notification.service.NotificationSettingService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 내 알림 설정 (IKA-88). 쪽지함 화면의 메일 알림 토글이 쓴다. */
@RestController
@RequestMapping("/api/v1/members/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService settingService;

    @GetMapping
    public ResponseEntity<NotificationSettingResponse> get(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(settingService.get(userDetails.getMember().getId()));
    }

    @PutMapping
    public ResponseEntity<NotificationSettingResponse> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody NotificationSettingUpdateRequest request) {
        return ResponseEntity.ok(
                settingService.update(userDetails.getMember().getId(), request.dmEmailEnabled()));
    }
}
