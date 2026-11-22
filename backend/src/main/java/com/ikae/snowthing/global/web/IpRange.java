package com.ikae.snowthing.global.web;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

/**
 * IPv4, IPv6 주소 하나 또는 CIDR 대역 (IKA-96).
 *
 * <p>숫자 주소만 받는다. 호스트 이름을 넣어도 DNS를 조회하지 않게 {@link InetAddress#getByName}에 넘기기 전에 글자를 거른다. 대역의 호스트
 * 비트는 0으로 지워 같은 대역을 같은 값으로 저장한다. IPv4에 대응한 IPv6 주소(::ffff:1.2.3.4)는 Java가 IPv4로 바꿔 주므로 IPv4 대역과
 * 비교된다.
 */
public final class IpRange {

    private final byte[] network;
    private final int prefixLength;

    private IpRange(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }

    /** "1.2.3.4", "1.2.3.0/24", "2001:db8::/64" 같은 값을 읽는다. 형식이 틀리면 빈 값. */
    public static Optional<IpRange> parse(String value) {
        if (value == null) return Optional.empty();
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 64) return Optional.empty();
        String addressPart = trimmed;
        Integer prefix = null;
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            addressPart = trimmed.substring(0, slash);
            String prefixPart = trimmed.substring(slash + 1);
            if (!prefixPart.matches("[0-9]{1,3}")) return Optional.empty();
            prefix = Integer.parseInt(prefixPart);
        }
        byte[] address = parseAddress(addressPart);
        if (address == null) return Optional.empty();
        int maxPrefix = address.length * 8;
        int prefixLength = prefix == null ? maxPrefix : prefix;
        if (prefixLength < 0 || prefixLength > maxPrefix) return Optional.empty();
        return Optional.of(new IpRange(mask(address, prefixLength), prefixLength));
    }

    /** 요청 IP 문자열이 이 대역 안에 있는지. IP가 아닌 값은 false. */
    public boolean contains(String ip) {
        byte[] address = parseAddress(ip == null ? null : ip.strip());
        return address != null && contains(address);
    }

    public boolean contains(byte[] address) {
        if (address.length != network.length) return false;
        return Arrays.equals(mask(address, prefixLength), network);
    }

    public boolean isIpv6() {
        return network.length == 16;
    }

    public int prefixLength() {
        return prefixLength;
    }

    /** 단일 주소면 주소만, 대역이면 "주소/길이"로 적는다. IPv6은 가장 긴 0 묶음을 ::로 줄인다. */
    public String toCanonicalString() {
        String address = isIpv6() ? formatIpv6(network) : formatIpv4(network);
        return prefixLength == network.length * 8 ? address : address + "/" + prefixLength;
    }

    /** 숫자 IP를 바이트로 바꾼다. 호스트 이름, 빈 값, 잘못된 형식은 null. */
    static byte[] parseAddress(String value) {
        if (value == null || value.isEmpty() || !value.matches("[0-9a-fA-F:.]+")) return null;
        if (!value.contains(":")) {
            if (!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return null;
            String[] octets = value.split("\\.");
            byte[] address = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(octets[i]);
                if (octet > 255) return null;
                address[i] = (byte) octet;
            }
            return address;
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException invalid) {
            return null;
        }
    }

    private static byte[] mask(byte[] address, int prefixLength) {
        byte[] masked = address.clone();
        for (int bit = prefixLength; bit < masked.length * 8; bit++) {
            masked[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
        }
        return masked;
    }

    private static String formatIpv4(byte[] address) {
        return (address[0] & 0xff)
                + "."
                + (address[1] & 0xff)
                + "."
                + (address[2] & 0xff)
                + "."
                + (address[3] & 0xff);
    }

    private static String formatIpv6(byte[] address) {
        int[] groups = new int[8];
        for (int i = 0; i < 8; i++) {
            groups[i] = ((address[i * 2] & 0xff) << 8) | (address[i * 2 + 1] & 0xff);
        }
        int bestStart = -1;
        int bestLength = 0;
        for (int i = 0; i < 8; ) {
            if (groups[i] != 0) {
                i++;
                continue;
            }
            int start = i;
            while (i < 8 && groups[i] == 0) i++;
            if (i - start > bestLength && i - start >= 2) {
                bestStart = start;
                bestLength = i - start;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i == bestStart) {
                text.append("::");
                i += bestLength - 1;
                continue;
            }
            if (!text.isEmpty() && text.charAt(text.length() - 1) != ':') text.append(':');
            text.append(Integer.toHexString(groups[i]));
        }
        return text.toString();
    }
}
