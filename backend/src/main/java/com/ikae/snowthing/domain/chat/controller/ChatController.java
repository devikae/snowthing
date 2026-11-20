package com.ikae.snowthing.domain.chat.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.ikae.snowthing.domain.chat.bot.BotMentionService;
import com.ikae.snowthing.domain.chat.dto.ChatErrorResponse;
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

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final BotMentionService botMentionService;
    private final ChatSessionRevocationRegistry chatSessionRevocationRegistry;
    private final MemberRepository memberRepository;

    @MessageMapping("/chat/messages")
    @SendTo("/sub/chat/main")
    public ChatMessageResponse handleChatMessage(
            @Payload ChatMessageRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor) {

        CustomUserDetails userDetails = null;
        if (principal instanceof Authentication auth
                && auth.getPrincipal() instanceof CustomUserDetails cud) {
            userDetails = cud;
        }

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        String clientIp = null;
        if (sessionAttributes != null) {
            String httpSessionId =
                    (String) sessionAttributes.get(ChatHandshakeInterceptor.ATTR_HTTP_SESSION_ID);
            if (chatSessionRevocationRegistry.isRevoked(httpSessionId)) {
                throw new CustomException(ErrorCode.CHAT_UNAUTHORIZED);
            }
            clientIp = (String) sessionAttributes.get(ChatHandshakeInterceptor.ATTR_CLIENT_IP);
        }

        // 연결의 Principal은 로그인 때 회원 스냅샷이다. 정지 여부와 닉네임은 메시지마다 현재 값으로 읽는다.
        // 접속 뒤 닉네임을 바꿨는데 예전 닉네임으로 나가면 다른 사람이 알아보지 못한다.
        if (userDetails != null && userDetails.getMember() != null) {
            Member current =
                    memberRepository
                            .findById(userDetails.getMember().getId())
                            .filter(member -> member.getStatus() == MemberStatus.ACTIVE)
                            .orElseThrow(() -> new CustomException(ErrorCode.CHAT_UNAUTHORIZED));
            userDetails = new CustomUserDetails(current);
        }

        ChatMessageResponse response = chatService.processMessage(request, userDetails, clientIp);
        botMentionService.submit(response, userDetails);
        return response;
    }

    @MessageExceptionHandler(CustomException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleChatException(CustomException ex) {
        return ChatErrorResponse.from(ex.getErrorCode());
    }
}
