package com.ikae.snowthing.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 테스트용 가짜 SES v2 HTTP 서버. 받은 HTTP 요청 수와 "수락" 건수를 따로 센다. 앱이 본 결과(타임아웃 등)와 공급자 쪽 실제 수락을 비교하기 위해서다.
 *
 * <ul>
 *   <li>ACCEPT: 수락하고 200과 MessageId를 돌려준다
 *   <li>THROTTLE: 429 TooManyRequestsException. 수락하지 않는다
 *   <li>REJECT: 400 MessageRejected. 수락하지 않는다
 *   <li>SERVER_ERROR: 500. 수락하지 않는다(앱은 구분할 수 없다)
 *   <li>DROP_BEFORE_ACCEPT: 수락하지 않고 응답 없이 기다리다 끊는다. 앱은 타임아웃(케이스 A)
 *   <li>ACCEPT_THEN_HANG: 수락한 뒤 응답 없이 기다리다 끊는다. 앱은 타임아웃(케이스 B)
 * </ul>
 */
public final class FakeSesServer implements AutoCloseable {

    public enum Mode {
        ACCEPT,
        THROTTLE,
        REJECT,
        SERVER_ERROR,
        DROP_BEFORE_ACCEPT,
        ACCEPT_THEN_HANG
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger accepted = new AtomicInteger();
    private final List<String> acceptedBodies = Collections.synchronizedList(new ArrayList<>());
    private volatile Mode mode = Mode.ACCEPT;
    private volatile Duration hang = Duration.ofSeconds(3);
    private volatile Duration acceptDelay = Duration.ZERO;

    public FakeSesServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void reset(Mode newMode) {
        mode = newMode;
        acceptDelay = Duration.ZERO;
        requests.set(0);
        accepted.set(0);
        acceptedBodies.clear();
    }

    /** ACCEPT 응답 전에 기다릴 시간. 두 워커의 호출 구간을 겹치게 만들 때 쓴다. */
    public void acceptDelay(Duration delay) {
        acceptDelay = delay;
    }

    public int requests() {
        return requests.get();
    }

    public int accepted() {
        return accepted.get();
    }

    public List<String> acceptedBodies() {
        synchronized (acceptedBodies) {
            return List.copyOf(acceptedBodies);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        requests.incrementAndGet();
        Mode current = mode;
        try {
            switch (current) {
                case ACCEPT -> {
                    sleep(acceptDelay);
                    int n = accepted.incrementAndGet();
                    acceptedBodies.add(body);
                    respond(exchange, 200, null, "{\"MessageId\":\"fake-" + n + "\"}");
                }
                case THROTTLE ->
                        respond(
                                exchange,
                                429,
                                "TooManyRequestsException",
                                "{\"message\":\"slow down\"}");
                case REJECT ->
                        respond(exchange, 400, "MessageRejected", "{\"message\":\"rejected\"}");
                case SERVER_ERROR ->
                        respond(
                                exchange,
                                500,
                                "InternalServiceErrorException",
                                "{\"message\":\"internal\"}");
                case DROP_BEFORE_ACCEPT -> sleep(hang);
                case ACCEPT_THEN_HANG -> {
                    accepted.incrementAndGet();
                    acceptedBodies.add(body);
                    sleep(hang);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String errorType, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (errorType != null) {
            exchange.getResponseHeaders().add("x-amzn-ErrorType", errorType);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
