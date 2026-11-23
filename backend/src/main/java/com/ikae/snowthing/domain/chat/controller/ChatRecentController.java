package com.ikae.snowthing.domain.chat.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

/** 실시간 라이브톡 최근 대화 조회 REST API. 비로그인 게스트 및 신규 접속/새로고침 유저에게 최근 30개 대화를 즉시 제공합니다. */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRecentController {

    private final ChatService chatService;
    private final ClientIpResolver clientIpResolver;

    /** 내 라이브톡 이름 (IKA-115). 회원은 null, 비회원은 이 IP로 보내면 받을 익명 닉네임이다. IP 원문은 내보내지 않는다. */
    public record ChatMeResponse(String guestNickname) {}

    @GetMapping("/recent")
    public ResponseEntity<List<ChatMessageResponse>> getRecentMessages() {
        return ResponseEntity.ok(chatService.getRecentMessages());
    }

    @GetMapping("/me")
    public ResponseEntity<ChatMeResponse> me(
            @AuthenticationPrincipal CustomUserDetails userDetails, HttpServletRequest request) {
        String guestNickname =
                userDetails != null
                        ? null
                        : chatService.guestNickname(clientIpResolver.resolve(request));
        // IP마다 답이 다르므로 공유 캐시에 남지 않게 한다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ChatMeResponse(guestNickname));
    }
}
