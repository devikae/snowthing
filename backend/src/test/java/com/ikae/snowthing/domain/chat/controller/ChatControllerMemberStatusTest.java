package com.ikae.snowthing.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.chat.bot.BotMentionService;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.config.websocket.ChatHandshakeInterceptor;
import com.ikae.snowthing.global.config.websocket.ChatSessionRevocationRegistry;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

class ChatControllerMemberStatusTest {

    private static final long MEMBER_ID = 7L;
    private static final ChatMessageRequest REQUEST = new ChatMessageRequest(null, "안녕하세요");

    private ChatService chatService;
    private BotMentionService botMentionService;
    private MemberRepository memberRepository;
    private ChatController controller;
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        botMentionService = mock(BotMentionService.class);
        memberRepository = mock(MemberRepository.class);
        controller =
                new ChatController(
                        chatService,
                        botMentionService,
                        new ChatSessionRevocationRegistry(),
                        memberRepository);
        // 접속 시점의 스냅샷은 활성이다. 정지 뒤에도 이 값은 바뀌지 않는다.
        Member snapshot =
                Member.builder().email("a@snowthing.com").nickname("보더").password("x").build();
        ReflectionTestUtils.setField(snapshot, "id", MEMBER_ID);
        CustomUserDetails details = new CustomUserDetails(snapshot);
        principal =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    @DisplayName("연결의 스냅샷이 활성이어도 현재 상태가 정지면 메시지를 거부한다")
    void suspendedMemberCannotSendThroughOpenConnection() {
        given(memberRepository.findStatusById(MEMBER_ID))
                .willReturn(Optional.of(MemberStatus.SUSPENDED));

        assertThatThrownBy(() -> controller.handleChatMessage(REQUEST, principal, headers()))
                .isInstanceOf(CustomException.class)
                .extracting(thrown -> ((CustomException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_UNAUTHORIZED);
        verify(chatService, never()).processMessage(any(), any(), any());
    }

    @Test
    @DisplayName("현재 상태가 활성이면 메시지를 처리한다")
    void activeMemberCanSend() {
        ChatMessageResponse response = mock(ChatMessageResponse.class);
        given(memberRepository.findStatusById(MEMBER_ID))
                .willReturn(Optional.of(MemberStatus.ACTIVE));
        given(chatService.processMessage(any(), any(), any())).willReturn(response);

        assertThat(controller.handleChatMessage(REQUEST, principal, headers())).isSameAs(response);
    }

    private static SimpMessageHeaderAccessor headers() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(ChatHandshakeInterceptor.ATTR_HTTP_SESSION_ID, "http-session");
        attributes.put(ChatHandshakeInterceptor.ATTR_CLIENT_IP, "203.0.113.10");
        accessor.setSessionAttributes(attributes);
        return accessor;
    }
}
