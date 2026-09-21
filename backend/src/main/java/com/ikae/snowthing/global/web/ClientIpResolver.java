package com.ikae.snowthing.global.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    public static final String DEFAULT_LOCAL_IP = "127.0.0.1";
    private static final String UNKNOWN = "unknown";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV6_LOOPBACK = "::1";
    private static final String IPV6_MAPPED_LOOPBACK = "0:0:0:0:0:0:0:1";

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!hasValidIp(remoteAddress)) {
            return DEFAULT_LOCAL_IP;
        }

        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }

        String realIp = request.getHeader(X_REAL_IP);
        if (hasValidIp(realIp) && !isTrustedProxy(realIp.trim())) {
            return realIp.trim();
        }

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (hasValidIp(forwardedFor)) {
            String[] candidates = forwardedFor.split(",");
            for (int index = candidates.length - 1; index >= 0; index--) {
                String candidate = candidates[index].trim();
                if (hasValidIp(candidate) && !isTrustedProxy(candidate)) {
                    return candidate;
                }
            }
        }
        return remoteAddress;
    }

    private boolean hasValidIp(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    private boolean isTrustedProxy(String ip) {
        return IPV4_LOOPBACK.equals(ip)
                || IPV6_LOOPBACK.equals(ip)
                || IPV6_MAPPED_LOOPBACK.equals(ip);
    }
}
