package com.ikae.snowthing.domain.slopestatus.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.slopestatus.config.SlopeStatusProperties;
import com.sun.net.httpserver.HttpServer;

/**
 * 현황 페이지 호출 규칙 (IKA-118). 외부 사이트 대신 이 테스트가 띄운 로컬 서버를 부른다.
 *
 * <p>설계(docs/conception/030_slope_status/01_design.md)의 실패 판단: 연결 실패, 4xx/5xx, 빈 본문. 리다이렉트는 따르지
 * 않는다.
 */
class SlopeStatusHttpClientTest {

    private HttpServer server;
    private SlopeStatusHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.start();
        client = new SlopeStatusHttpClient(new SlopeStatusProperties(List.of(), 1000, 500));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("200 응답의 본문을 그대로 돌려주고 User-Agent로 서비스를 밝힌다")
    void returnsBodyAndIdentifiesItself() {
        AtomicReference<String> userAgent = new AtomicReference<>();
        respond(
                "/ok",
                200,
                "<table>현황</table>",
                exchangeUserAgent -> userAgent.set(exchangeUserAgent));

        byte[] body = client.get(uri("/ok"));

        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("<table>현황</table>");
        assertThat(userAgent.get()).isEqualTo(SlopeStatusHttpClient.USER_AGENT);
    }

    @Test
    @DisplayName("4xx와 5xx는 오류 본문이 있어도 실패다")
    void errorStatusFails() {
        respond("/missing", 404, "<html>없는 페이지</html>", ignored -> {});
        respond("/broken", 503, "<html>점검 중</html>", ignored -> {});

        assertThatThrownBy(() -> client.get(uri("/missing")))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(() -> client.get(uri("/broken")))
                .isInstanceOf(SlopeFetchException.class);
    }

    @Test
    @DisplayName("본문이 빈 200은 실패다")
    void emptyBodyFails() {
        respond("/empty", 200, "", ignored -> {});

        assertThatThrownBy(() -> client.get(uri("/empty"))).isInstanceOf(SlopeFetchException.class);
    }

    @Test
    @DisplayName("리다이렉트는 따라가지 않는다. 옮겨 간 주소를 부르지 않고 실패로 끝난다")
    void doesNotFollowRedirect() {
        AtomicInteger movedCalls = new AtomicInteger();
        server.createContext(
                "/old",
                exchange -> {
                    exchange.getResponseHeaders().add("Location", "/new");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        respond("/new", 200, "<table>다른 곳</table>", ignored -> movedCalls.incrementAndGet());

        assertThatThrownBy(() -> client.get(uri("/old"))).isInstanceOf(SlopeFetchException.class);
        assertThat(movedCalls).hasValue(0);
    }

    @Test
    @DisplayName("연결할 수 없으면 실패이고, 사유에는 예외 종류만 남는다")
    void connectionFailureFails() {
        URI closed = uri("/ok");
        server.stop(0);

        assertThatThrownBy(() -> client.get(closed))
                .isInstanceOf(SlopeFetchException.class)
                .hasMessageStartingWith("외부 호출 실패: ")
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("localhost");
    }

    @Test
    @DisplayName("응답이 읽기 제한 시간을 넘으면 기다리지 않고 실패다")
    void readTimeoutFails() {
        server.createContext(
                "/slow",
                exchange -> {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });

        assertThatThrownBy(() -> client.get(uri("/slow"))).isInstanceOf(SlopeFetchException.class);
    }

    private void respond(
            String path, int status, String body, java.util.function.Consumer<String> onRequest) {
        server.createContext(
                path,
                exchange -> {
                    onRequest.accept(exchange.getRequestHeaders().getFirst("User-Agent"));
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                    if (bytes.length > 0) {
                        exchange.getResponseBody().write(bytes);
                    }
                    exchange.close();
                });
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
