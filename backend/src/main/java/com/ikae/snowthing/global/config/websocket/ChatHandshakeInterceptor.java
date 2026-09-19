package com.ikae.snowthing.global.config.websocket;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ChatHandshakeInterceptor extends HttpSessionHandshakeInterceptor {

    public static final String ATTR_CLIENT_IP = "clientIp";

    private final ClientIpResolver clientIpResolver;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes)
            throws Exception {
        super.beforeHandshake(request, response, wsHandler, attributes);

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
            String clientIp = clientIpResolver.resolve(httpServletRequest);
            attributes.put(ATTR_CLIENT_IP, clientIp);
        }

        return true;
    }
}
