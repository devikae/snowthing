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

import com.ikae.snowthing.domain.chat.dto.ChatErrorResponse;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
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
    private final ChatSessionRevocationRegistry chatSessionRevocationRegistry;

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

        return chatService.processMessage(request, userDetails, clientIp);
    }

    @MessageExceptionHandler(CustomException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleChatException(CustomException ex) {
        return ChatErrorResponse.from(ex.getErrorCode());
    }
}
