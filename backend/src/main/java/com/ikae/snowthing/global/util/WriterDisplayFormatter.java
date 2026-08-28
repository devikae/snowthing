package com.ikae.snowthing.global.util;

import com.ikae.snowthing.domain.member.entity.Member;

/** 작성자 표시명 포맷팅 및 IP 마스킹 공통 유틸리티 */
public final class WriterDisplayFormatter {

    private static final String ANONYMOUS_FORMAT = "익명 (%s)";
    private static final String DEFAULT_MASKED_IP = "127.0.***.***";
    private static final String UNKNOWN_WRITER = "알 수 없음";

    private WriterDisplayFormatter() {}

    public static String format(boolean isAnonymous, Member member, String writerIp) {
        if (isAnonymous) {
            return formatAnonymous(writerIp);
        }
        return (member != null && member.getNickname() != null)
                ? member.getNickname()
                : UNKNOWN_WRITER;
    }

    public static String formatAnonymous(String writerIp) {
        return String.format(ANONYMOUS_FORMAT, maskIp(writerIp));
    }

    public static String maskIp(String ip) {
        if (ip == null || ip.isBlank() || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return DEFAULT_MASKED_IP;
        }

        // IPv4 처리 (4개 옥텟)
        String[] ipv4Parts = ip.split("\\.");
        if (ipv4Parts.length == 4) {
            return ipv4Parts[0] + "." + ipv4Parts[1] + ".***.***";
        }

        // IPv6 처리 (콜론 구분)
        if (ip.contains(":")) {
            String[] ipv6Parts = ip.split(":");
            if (ipv6Parts.length >= 2 && !ipv6Parts[0].isBlank() && !ipv6Parts[1].isBlank()) {
                return ipv6Parts[0] + ":" + ipv6Parts[1] + ":****:****";
            }
        }

        // 비정상 포맷의 경우 원문 노출 방지를 위해 기본 마스킹값 반환
        return DEFAULT_MASKED_IP;
    }
}
