package com.ikae.snowthing.domain.chat.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;

import lombok.RequiredArgsConstructor;

/** 실시간 라이브톡 최근 대화 조회 REST API. 비로그인 게스트 및 신규 접속/새로고침 유저에게 최근 30개 대화를 즉시 제공합니다. */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRecentController {

    private final ChatService chatService;

    @GetMapping("/recent")
    public ResponseEntity<List<ChatMessageResponse>> getRecentMessages() {
        return ResponseEntity.ok(chatService.getRecentMessages());
    }
}
