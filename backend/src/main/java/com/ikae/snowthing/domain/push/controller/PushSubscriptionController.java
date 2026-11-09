package com.ikae.snowthing.domain.push.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.push.dto.PushConfigResponse;
import com.ikae.snowthing.domain.push.dto.PushSubscriptionRequest;
import com.ikae.snowthing.domain.push.dto.PushUnsubscribeRequest;
import com.ikae.snowthing.domain.push.service.PushSubscriptionService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 휴대폰 알림(웹 푸시) 구독. 모두 로그인 필수이고 비로그인은 보안 설정의 기본 규칙(authenticated)으로 401이다. */
@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;

    @GetMapping("/config")
    public ResponseEntity<PushConfigResponse> config(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(pushSubscriptionService.config(userDetails));
    }

    @PutMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        pushSubscriptionService.subscribe(userDetails, request);
        return ResponseEntity.noContent().build();
    }

    /** endpoint가 길어 경로나 쿼리 대신 본문으로 받는다. */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(
            @Valid @RequestBody PushUnsubscribeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        pushSubscriptionService.unsubscribe(userDetails, request);
        return ResponseEntity.noContent().build();
    }
}
