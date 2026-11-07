package com.ikae.snowthing.global.web;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
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
    private static final IpAddressMatcher PRIVATE_CLASS_A = new IpAddressMatcher("10.0.0.0/8");
    private static final IpAddressMatcher PRIVATE_CLASS_B = new IpAddressMatcher("172.16.0.0/12");
    private static final IpAddressMatcher PRIVATE_CLASS_C = new IpAddressMatcher("192.168.0.0/16");

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

    /** 요청 제한 키로 쓸 IP 형식. IP가 아닌 값은 모두 unknown 한 곳으로 묶어 값을 바꿔도 새 버킷이 생기지 않게 한다. */
    public static String normalizeForLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return "unknown";
        try {
            // Never resolve hostnames supplied by a request. Only numeric IPv4/IPv6 literals are
            // accepted.
            if (!clientIp.matches("[0-9a-fA-F:.]+")) return "unknown";
            byte[] address;
            if (!clientIp.contains(":")) {
                if (!clientIp.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return "unknown";
                String[] octets = clientIp.split("\\.");
                address = new byte[4];
                for (int i = 0; i < octets.length; i++) {
                    int octet = Integer.parseInt(octets[i]);
                    if (octet > 255) return "unknown";
                    address[i] = (byte) octet;
                }
            } else {
                address = java.net.InetAddress.getByName(clientIp).getAddress();
            }
            // Group IPv6 privacy addresses by /64 so rotating interface identifiers cannot reset a
            // bucket.
            if (address.length == 16) java.util.Arrays.fill(address, 8, 16, (byte) 0);
            return java.util.HexFormat.of().formatHex(address);
        } catch (java.net.UnknownHostException invalidIp) {
            return "unknown";
        }
    }

    private boolean hasValidIp(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    private boolean isTrustedProxy(String ip) {
        return IPV4_LOOPBACK.equals(ip)
                || IPV6_LOOPBACK.equals(ip)
                || IPV6_MAPPED_LOOPBACK.equals(ip)
                || matchesPrivateNetwork(PRIVATE_CLASS_A, ip)
                || matchesPrivateNetwork(PRIVATE_CLASS_B, ip)
                || matchesPrivateNetwork(PRIVATE_CLASS_C, ip);
    }

    private boolean matchesPrivateNetwork(IpAddressMatcher matcher, String ip) {
        try {
            return matcher.matches(ip);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
