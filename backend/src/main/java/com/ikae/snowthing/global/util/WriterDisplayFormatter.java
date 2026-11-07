package com.ikae.snowthing.global.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

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
        if (member != null && member.isWithdrawn()) {
            return Member.WITHDRAWN_NICKNAME;
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

        // IPv6 주소를 먼저 처리해 IPv4 매핑 주소도 올바르게 구분한다.
        if (ip.contains(":")) {
            return maskIpv6AsIpv4(ip);
        }

        // IPv4 처리 (4개 옥텟)
        String[] ipv4Parts = ip.split("\\.");
        if (ipv4Parts.length == 4) {
            return ipv4Parts[0] + "." + ipv4Parts[1] + ".***.***";
        }

        // 비정상 포맷의 경우 원문 노출 방지를 위해 기본 마스킹값 반환
        return DEFAULT_MASKED_IP;
    }

    private static String maskIpv6AsIpv4(String ip) {
        // 숫자 IP 리터럴만 허용해 DNS 조회를 유발하지 않는다.
        if (!ip.matches("[0-9a-fA-F:.]+")) {
            return DEFAULT_MASKED_IP;
        }
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length == 4) {
                return Byte.toUnsignedInt(bytes[0])
                        + "."
                        + Byte.toUnsignedInt(bytes[1])
                        + ".***.***";
            }
            long value = UUID.nameUUIDFromBytes(bytes).getMostSignificantBits();
            return ((value >>> 56) & 0xff) + "." + ((value >>> 48) & 0xff) + ".***.***";
        } catch (UnknownHostException exception) {
            return DEFAULT_MASKED_IP;
        }
    }
}
