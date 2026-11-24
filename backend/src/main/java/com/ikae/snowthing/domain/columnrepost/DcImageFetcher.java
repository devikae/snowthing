package com.ikae.snowthing.domain.columnrepost;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 디시 사진, GIF, 짧은 mp4 클립, 동영상 플레이어 영상을 받는다. 디시는 다른 사이트에서 주소를 바로 불러오면 막으므로 원문 글 주소를 Referer로 붙인다.
 *
 * <p>대기열 본문은 운영자가 넣지만, 서버가 임의 주소로 요청을 보내지 않도록 디시 미디어 호스트만 https로 허용하고 리다이렉트는 따라가지 않는다.
 *
 * <p>GIF와 영상은 수 MB에서 수십 MB라 메모리에 올리지 않고 임시 파일로 받는다. 상한을 넘으면 그 자리에서 끊는다.
 */
@Component
public class DcImageFetcher {

    /** 눈팅 사진 준비 과정(크기 조정)에 넣는 사진의 상한. 회원 업로드와 같다. */
    static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024L;

    /** 그대로 옮기는 GIF와 영상 한 개의 상한. */
    static final long MAX_MEDIA_BYTES = 30 * 1024 * 1024L;

    private static final long MAX_PAGE_BYTES = 512 * 1024L;
    private static final Pattern IMAGE_HOST =
            Pattern.compile("^(dcimg\\d*|image)\\.dcinside\\.(com|co\\.kr)$");
    private static final Pattern MOVIE_FILE_HOST =
            Pattern.compile("^dcm\\d*\\.dcinside\\.(com|co\\.kr)$");
    private static final Pattern MOVIE_PAGE_QUERY = Pattern.compile("^no=\\d{1,12}$");
    private static final Pattern MOVIE_SOURCE = Pattern.compile("<source\\s+src=\"([^\"]+)\"");
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

    /** 파일 앞부분으로 정한 형식. extension이 null이면 옮기지 않는 형식이다. */
    public enum Kind {
        JPG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
        GIF("gif", "image/gif"),
        MP4("mp4", "video/mp4"),
        UNKNOWN(null, null);

        private final String extension;
        private final String contentType;

        Kind(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }

        public boolean isStillImage() {
            return this == JPG || this == PNG || this == WEBP;
        }
    }

    /** 받은 파일. 다 쓰면 close로 임시 파일을 지운다. 상한을 넘었으면 tooLarge이고 파일은 이미 지워져 있다. */
    public record Downloaded(Kind kind, Path file, long size, boolean tooLarge)
            implements AutoCloseable {
        public byte[] bytes() throws IOException {
            return Files.readAllBytes(file);
        }

        @Override
        public void close() throws IOException {
            if (file != null) Files.deleteIfExists(file);
        }
    }

    /** 디시 사진 서버(사진, GIF, GIF를 바꾼 mp4 클립) 주소. */
    static boolean isAllowed(String url) {
        return hostMatches(url, IMAGE_HOST);
    }

    /** 디시 동영상 플레이어 페이지 주소. 실제 mp4 주소는 이 페이지 안에 있다. */
    static boolean isMoviePage(String url) {
        URI uri = parse(url);
        return uri != null
                && safe(uri)
                && "gall.dcinside.com".equalsIgnoreCase(uri.getHost())
                && "/board/movie/movie_view".equals(uri.getPath())
                && uri.getRawQuery() != null
                && MOVIE_PAGE_QUERY.matcher(uri.getRawQuery()).matches();
    }

    /** 디시 동영상 파일 서버 주소. */
    static boolean isMovieFile(String url) {
        return hostMatches(url, MOVIE_FILE_HOST);
    }

    /**
     * @throws IOException 받지 못했을 때. 다음 게시 시각에 다시 시도한다.
     */
    public Downloaded download(String url, String referer, long maxBytes) throws IOException {
        if (!isAllowed(url) && !isMovieFile(url)) {
            throw new IOException("허용하지 않은 미디어 주소");
        }
        HttpResponse<InputStream> response = send(url, referer);
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("미디어 응답 " + response.statusCode());
            }
            return save(body, maxBytes);
        }
    }

    /** 응답을 임시 파일에 받는다. 상한을 넘으면 그 자리에서 멈추고 파일을 지운다. */
    static Downloaded save(InputStream body, long maxBytes) throws IOException {
        Path file = Files.createTempFile("column-media-", ".bin");
        try (OutputStream out = Files.newOutputStream(file)) {
            byte[] head = new byte[16];
            int headLength = 0;
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = body.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    out.close();
                    Files.deleteIfExists(file);
                    return new Downloaded(Kind.UNKNOWN, null, total, true);
                }
                if (headLength < head.length) {
                    int copy = Math.min(read, head.length - headLength);
                    System.arraycopy(buffer, 0, head, headLength, copy);
                    headLength += copy;
                }
                out.write(buffer, 0, read);
            }
            return new Downloaded(detect(Arrays.copyOf(head, headLength)), file, total, false);
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(file);
            throw failure;
        }
    }

    /**
     * 동영상 플레이어 페이지에서 실제 mp4 주소를 찾는다. 주소에는 디시가 매번 새로 붙이는 서명 키가 있어 게시하는 순간에 찾는다.
     *
     * @return 디시 동영상 파일 서버 주소. 페이지에 영상이 없으면(지워진 영상 등) 빈 값
     */
    public Optional<String> movieFileUrl(String pageUrl, String referer) throws IOException {
        if (!isMoviePage(pageUrl)) {
            throw new IOException("허용하지 않은 동영상 페이지");
        }
        HttpResponse<InputStream> response = send(pageUrl, referer);
        String page;
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("동영상 페이지 응답 " + response.statusCode());
            }
            page = new String(body.readNBytes((int) MAX_PAGE_BYTES), StandardCharsets.UTF_8);
        }
        return movieSource(page);
    }

    static Optional<String> movieSource(String page) {
        Matcher matcher = MOVIE_SOURCE.matcher(page);
        if (!matcher.find()) return Optional.empty();
        String url = matcher.group(1).replace("&amp;", "&");
        return isMovieFile(url) ? Optional.of(url) : Optional.empty();
    }

    /** 응답 헤더 대신 파일 앞부분으로 형식을 정한다. 디시는 Content-Type을 일정하게 주지 않는다. */
    static Kind detect(byte[] head) {
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) return Kind.JPG;
        if (startsWith(head, 0, 0x89, 'P', 'N', 'G')) return Kind.PNG;
        if (startsWith(head, 0, 'R', 'I', 'F', 'F') && startsWith(head, 8, 'W', 'E', 'B', 'P'))
            return Kind.WEBP;
        if (startsWith(head, 0, 'G', 'I', 'F', '8')) return Kind.GIF;
        if (startsWith(head, 4, 'f', 't', 'y', 'p')) return Kind.MP4;
        return Kind.UNKNOWN;
    }

    private HttpResponse<InputStream> send(String url, String referer) throws IOException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeout)
                        .header("Referer", referer)
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("미디어 받기 중단", interrupted);
        }
    }

    private static boolean hostMatches(String url, Pattern host) {
        URI uri = parse(url);
        return uri != null
                && safe(uri)
                && host.matcher(uri.getHost().toLowerCase(Locale.ROOT)).matches();
    }

    private static boolean safe(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static URI parse(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, int offset, int... prefix) {
        if (bytes.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[offset + i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }
}
