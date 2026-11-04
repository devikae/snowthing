package com.ikae.snowthing.domain.message.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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

import com.ikae.snowthing.domain.message.dto.DirectMessageDetailResponse;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendResponse;
import com.ikae.snowthing.domain.message.dto.DirectMessageSummaryResponse;
import com.ikae.snowthing.domain.message.dto.UnreadMessageCountResponse;
import com.ikae.snowthing.domain.message.service.DirectMessageService;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

/** 회원 간 1:1 쪽지. 모두 로그인이 필요하다(SecurityConfig의 나머지 경로 authenticated). 쪽지는 개인 정보라 캐시하지 않는다. */
@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class DirectMessageController {

    private final DirectMessageService directMessageService;
    private final ContentWriteRateLimiter contentWriteRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<DirectMessageSendResponse> send(
            @Valid @RequestBody DirectMessageSendRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        contentWriteRateLimiter.check(
                userDetails,
                clientIpResolver.resolve(httpRequest),
                ContentWriteRateLimiter.Action.DIRECT_MESSAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(directMessageService.send(userDetails, request));
    }

    @GetMapping("/received")
    public ResponseEntity<CursorPageResponse<DirectMessageSummaryResponse>> getReceived(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return noStore(directMessageService.getReceived(userDetails, cursor, size));
    }

    @GetMapping("/sent")
    public ResponseEntity<CursorPageResponse<DirectMessageSummaryResponse>> getSent(
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return noStore(directMessageService.getSent(userDetails, cursor, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadMessageCountResponse> countUnread(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return noStore(directMessageService.countUnread(userDetails));
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<DirectMessageDetailResponse> getDetail(
            @PathVariable Long messageId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return noStore(directMessageService.getDetail(userDetails, messageId));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long messageId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        directMessageService.delete(userDetails, messageId);
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
