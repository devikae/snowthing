package com.ikae.snowthing.domain.notification.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.notification.dto.NotificationListResponse;
import com.ikae.snowthing.domain.notification.dto.UnreadCountResponse;
import com.ikae.snowthing.domain.notification.service.NotificationService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationListResponse> list(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(notificationService.list(userDetails, cursor, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.unreadCount(userDetails));
    }

    @PatchMapping("/{publicId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID publicId) {
        notificationService.markRead(userDetails, publicId.toString());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllRead(userDetails);
        return ResponseEntity.noContent().build();
    }
}
