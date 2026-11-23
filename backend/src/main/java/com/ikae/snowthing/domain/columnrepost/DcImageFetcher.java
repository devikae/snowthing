package com.ikae.snowthing.domain.columnrepost;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 디시 사진을 받는다. 디시는 다른 사이트에서 사진 주소를 바로 불러오면 막으므로 원문 글 주소를 Referer로 붙인다.
 *
 * <p>대기열 본문은 운영자가 넣지만, 서버가 임의 주소로 요청을 보내지 않도록 디시 사진 호스트만 https로 허용하고 리다이렉트는 따라가지 않는다.
 */
@Component
public class DcImageFetcher {

    static final long MAX_BYTES = 5 * 1024 * 1024L;
    private static final Pattern ALLOWED_HOST =
            Pattern.compile("^(dcimg\\d*|image)\\.dcinside\\.(com|co\\.kr)$");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/130.0 Safari/537.36";

    private final HttpClient client;
    private final Duration timeout;

    @Autowired
    public DcImageFetcher(ColumnRepostProperties properties) {
        this(
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(properties.imageTimeout())
                        .build(),
                properties.imageTimeout());
    }

    DcImageFetcher(HttpClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    /** 받은 사진. 형식을 알 수 없거나 눈팅이 받지 않는 형식(gif 등)이면 extension이 null이다. */
    public record Fetched(String extension, String contentType, byte[] bytes) {
        public boolean supported() {
            return extension != null;
        }
    }

    static boolean isAllowed(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ALLOWED_HOST.matcher(uri.getHost().toLowerCase(Locale.ROOT)).matches();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * @throws IOException 받지 못했을 때. 다음 게시 시각에 다시 시도한다.
     */
    public Fetched fetch(String url, String referer) throws IOException {
        if (!isAllowed(url)) {
            throw new IOException("허용하지 않은 사진 주소");
        }
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Referer", referer)
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("사진 받기 중단", interrupted);
        }
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("사진 응답 " + response.statusCode());
            }
            byte[] bytes = body.readNBytes((int) MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) {
                return new Fetched(null, null, new byte[0]);
            }
            return detect(bytes);
        }
    }

    /** 응답 헤더 대신 파일 앞부분으로 형식을 정한다. 디시는 Content-Type을 일정하게 주지 않는다. */
    static Fetched detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return new Fetched("jpg", "image/jpeg", bytes);
        if (startsWith(bytes, 0x89, 'P', 'N', 'G')) return new Fetched("png", "image/png", bytes);
        if (bytes.length >= 12
                && startsWith(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') return new Fetched("webp", "image/webp", bytes);
        return new Fetched(null, null, bytes);
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
