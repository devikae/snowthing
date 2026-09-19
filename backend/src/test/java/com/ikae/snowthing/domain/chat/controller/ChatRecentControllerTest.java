package com.ikae.snowthing.domain.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatRecentControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ChatRecentHistoryBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer.clear();
    }

    @Test
    @DisplayName("GET /api/v1/chat/recent - 비로그인/신규 유저도 최근 30개 대화를 200 OK로 조회할 수 있다")
    void getRecentMessages_Success() throws Exception {
        ChatMessageResponse message =
                new ChatMessageResponse(
                        "msg-123",
                        new SenderDto("usr-1", "하이원러버"),
                        "HIGH1",
                        "아테나 오픈했습니다!",
                        "2026-09-20T01:30:00");

        buffer.append(message);

        mockMvc.perform(get("/api/v1/chat/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageId").value("msg-123"))
                .andExpect(jsonPath("$[0].sender.nickname").value("하이원러버"))
                .andExpect(jsonPath("$[0].resortTag").value("HIGH1"))
                .andExpect(jsonPath("$[0].content").value("아테나 오픈했습니다!"));
    }
}
