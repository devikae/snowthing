package com.ikae.snowthing.global.config.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_INTERVAL_MILLIS = 10_000L;
    private static final int HEARTBEAT_SCHEDULER_POOL_SIZE = 1;
    private static final int CHANNEL_CORE_POOL_SIZE = 4;
    private static final int CHANNEL_MAX_POOL_SIZE = 16;
    private static final int CHANNEL_QUEUE_CAPACITY = 500;
    private static final int SEND_TIME_LIMIT_MILLIS = 15_000;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 16 * 1024;

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
        registry.enableSimpleBroker("/sub", "/queue")
                .setHeartbeatValue(
                        new long[] {HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS})
                .setTaskScheduler(chatHeartbeatTaskScheduler());
        // Application destination prefix for messages handled by @MessageMapping
        registry.setApplicationDestinationPrefixes("/pub");
        // User destination prefix for private messages (e.g. /user/queue/errors)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        configureChannelExecutor(registration);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        configureChannelExecutor(registration);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setSendTimeLimit(SEND_TIME_LIMIT_MILLIS)
                .setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES)
                .setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
    }

    @Bean
    public TaskScheduler chatHeartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(HEARTBEAT_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    private void configureChannelExecutor(ChannelRegistration registration) {
        registration
                .taskExecutor()
                .corePoolSize(CHANNEL_CORE_POOL_SIZE)
                .maxPoolSize(CHANNEL_MAX_POOL_SIZE)
                .queueCapacity(CHANNEL_QUEUE_CAPACITY);
    }
}
