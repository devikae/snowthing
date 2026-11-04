package com.ikae.snowthing.domain.message.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.message.dto.UnreadMessageCountResponse;
import com.ikae.snowthing.domain.message.service.DirectMessageService;
import com.ikae.snowthing.global.exception.GlobalExceptionHandler;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.web.ClientIpResolver;

/** 응답 형태, 캐시 헤더, 잘못된 경로 값 처리만 본다. 거부 규칙은 DirectMessageServiceTest에서 본다. */
class DirectMessageControllerTest {

    private final DirectMessageService service = mock(DirectMessageService.class);
    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(
                            new DirectMessageController(
                                    service,
                                    mock(ContentWriteRateLimiter.class),
                                    mock(ClientIpResolver.class)))
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .setControllerAdvice(
                            new GlobalExceptionHandler(
                                    new StaticListableBeanFactory()
                                            .getBeanProvider(ServerErrorAlerter.class)))
                    .build();

    @Test
    @DisplayName("안 읽은 수는 캐시하지 않는다")
    void unreadCountIsNotCached() throws Exception {
        when(service.countUnread(any())).thenReturn(new UnreadMessageCountResponse(3));

        mockMvc.perform(get("/api/v1/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @DisplayName("숫자가 아닌 쪽지 ID는 500이 아니라 없는 쪽지와 같은 404다")
    void nonNumericMessageIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/messages/abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MESSAGE_001"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("숫자가 아닌 커서는 400이다")
    void nonNumericCursorIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/messages/received").param("cursor", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("받은함 커서와 크기를 서비스에 넘긴다")
    void receivedPassesCursor() throws Exception {
        mockMvc.perform(get("/api/v1/messages/received").param("cursor", "42").param("size", "10"))
                .andExpect(status().isOk());
        verify(service).getReceived(any(), eq(42L), eq(10));
    }
}
