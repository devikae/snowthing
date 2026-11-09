package com.ikae.snowthing.domain.push.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 구독 endpoint는 브라우저가 주지만 요청 본문으로 들어오므로 아무 주소나 넣을 수 있다. 서버가 그 주소로 POST를 보내므로, 내부망이나 임의 서버를 두드리는
 * 통로(SSRF)가 되지 않게 알려진 브라우저 푸시 서비스의 https 주소만 받는다.
 */
final class PushEndpointPolicy {

    /** Chrome, Edge(Chromium), 삼성 인터넷은 FCM, Safari는 Apple, Firefox는 Mozilla, 구 Edge는 WNS를 쓴다. */
    private static final List<String> ALLOWED_HOST_SUFFIXES =
            List.of(
                    "fcm.googleapis.com",
                    "push.apple.com",
                    "push.services.mozilla.com",
                    "notify.windows.com");

    private PushEndpointPolicy() {}

    /** 허용하지 않는 주소면 null. */
    static URI parse(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return null;
        }
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed =
                ALLOWED_HOST_SUFFIXES.stream()
                        .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
        return allowed ? uri : null;
    }

    static String hash(String endpoint) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
