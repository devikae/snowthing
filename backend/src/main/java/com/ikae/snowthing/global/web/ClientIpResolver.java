package com.ikae.snowthing.global.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    public static final String DEFAULT_LOCAL_IP = "127.0.0.1";
    private static final String UNKNOWN = "unknown";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String PROXY_CLIENT_IP = "Proxy-Client-IP";

    public String resolve(HttpServletRequest request) {
        String ip = request.getHeader(X_FORWARDED_FOR);
        if (!hasValidIp(ip)) {
            ip = request.getHeader(PROXY_CLIENT_IP);
        }
        if (!hasValidIp(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!hasValidIp(ip)) {
            return DEFAULT_LOCAL_IP;
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex >= 0 ? ip.substring(0, commaIndex).trim() : ip;
    }

    private boolean hasValidIp(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip);
    }
}
