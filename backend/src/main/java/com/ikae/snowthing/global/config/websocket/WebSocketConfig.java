package com.ikae.snowthing.global.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatHandshakeInterceptor chatHandshakeInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Raw WebSocket endpoint
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "https://snowthing.org",
                        "https://www.snowthing.org",
                        "http://localhost:*")
                .addInterceptors(chatHandshakeInterceptor);

        // SockJS fallback endpoint
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(
                        "http://localhost:3000",
                        "https://snowthing.org",
                        "https://www.snowthing.org",
                        "http://localhost:*")
                .addInterceptors(chatHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory message broker for subscription
        registry.enableSimpleBroker("/sub", "/queue");
        // Application destination prefix for messages handled by @MessageMapping
        registry.setApplicationDestinationPrefixes("/pub");
        // User destination prefix for private messages (e.g. /user/queue/errors)
        registry.setUserDestinationPrefix("/user");
    }
}
