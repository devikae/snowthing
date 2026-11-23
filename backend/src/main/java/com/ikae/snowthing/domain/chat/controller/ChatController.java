package com.ikae.snowthing.domain.chat.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.SimpMessageType;
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
    private final SimpMessageSendingOperations messagingTemplate;

    static final String MAIN_DESTINATION = "/sub/chat/main";

    /** 금칙어에 걸린 메시지를 보낸 사람 연결 하나에만 돌려주는 개인 큐 (IKA-115). 화면은 일반 메시지처럼 그린다. */
    static final String HELD_ECHO_DESTINATION = "/queue/chat";

    /** 반환값 대신 직접 보낸다. 금칙어에 걸린 메시지는 방송하지 않고 보낸 사람에게만 돌려줘야 해서다 (IKA-115). */
    @MessageMapping("/chat/messages")
    public void handleChatMessage(
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

        ChatService.SendResult result = chatService.send(request, userDetails, clientIp);
        if (result.held()) {
            // 보낸 사람 화면에만 돌려준다. 봇에도 넘기지 않는다.
            sendToSession(headerAccessor.getSessionId(), result.message());
            return;
        }
        messagingTemplate.convertAndSend(MAIN_DESTINATION, result.message());
        botMentionService.submit(result.message(), userDetails);
    }

    /**
     * 세션 하나로 보낸다. 사용자 이름 자리에 세션 ID를 넣고 같은 세션 ID 헤더를 붙이면 Spring이 그 연결 하나로만 보낸다. 비회원은 Principal이 없어 이
     * 방법만 된다. 회원이 여러 탭을 열었어도 보낸 탭에만 보인다.
     */
    private void sendToSession(String sessionId, ChatMessageResponse message) {
        if (sessionId == null) {
            return;
        }
        SimpMessageHeaderAccessor headers =
                SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(
                sessionId, HELD_ECHO_DESTINATION, message, headers.getMessageHeaders());
    }

    @MessageExceptionHandler(CustomException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleChatException(CustomException ex) {
        return ChatErrorResponse.from(ex.getErrorCode());
    }
}
