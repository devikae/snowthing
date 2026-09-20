package com.ikae.snowthing.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

@Tag("benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatLoadTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatLoadTest.class);
    private static final int USER_COUNT = 200;
    private static final int LOGIN_CONCURRENCY = 20;
    private static final int SERVER_MAX_CONNECTIONS = 500;
    private static final int EXPECTED_DELIVERY_COUNT = USER_COUNT * USER_COUNT;
    private static final int HTTP_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int CONNECTION_TIMEOUT_SECONDS = 30;
    private static final int SUBSCRIPTION_STABILIZATION_SECONDS = 2;
    private static final int DELIVERY_TIMEOUT_SECONDS = 30;
    private static final int HEARTBEAT_OBSERVATION_SECONDS = 12;
    private static final int METRIC_SAMPLE_INTERVAL_MILLIS = 100;
    private static final int COOKIE_SPLIT_LIMIT = 2;
    private static final int PERCENTILE_95 = 95;
    private static final int PERCENTILE_99 = 99;
    private static final int PERCENT_BASE = 100;
    private static final int CPU_PERMILLE_BASE = 1_000;
    private static final int BYTES_PER_MEBIBYTE = 1024 * 1024;
    private static final String PASSWORD = "LoadTest123!";
    private static final String CHAT_DESTINATION = "/pub/chat/messages";
    private static final String CHAT_SUBSCRIPTION = "/sub/chat/main";

    @DynamicPropertySource
    static void configureMySql(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        environmentOrDefault(
                                "SNOWTHING_TEST_DB_URL",
                                "jdbc:mysql://localhost:3306/snowthing_test?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul"));
        registry.add(
                "spring.datasource.username",
                () -> environmentOrDefault("SNOWTHING_TEST_DB_USERNAME", "snowuser"));
        registry.add(
                "spring.datasource.password",
                () -> environmentOrDefault("SNOWTHING_TEST_DB_PASSWORD", "snowthing_pass_2026!"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("snowthing.chat.max-connections", () -> SERVER_MAX_CONNECTIONS);
    }

    @LocalServerPort private int port;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository memberRepository;

    private WebSocketStompClient stompClient;
    private final List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        String encodedPassword = passwordEncoder.encode(PASSWORD);
        List<Member> members = new ArrayList<>(USER_COUNT);
        for (int index = 0; index < USER_COUNT; index++) {
            members.add(
                    Member.builder()
                            .email(email(index))
                            .password(encodedPassword)
                            .nickname(nickname(index))
                            .build());
        }
        memberRepository.saveAll(members);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        for (StompSession session : sessions) {
            if (session.isConnected()) {
                session.disconnect();
            }
        }
        if (stompClient != null) {
            stompClient.stop();
        }
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("회원 200명의 연결 유지와 동시 메시지 브로드캐스트 결과를 측정한다")
    void twoHundredMembersConnectAndBroadcastConcurrently() throws Exception {
        LoadMetrics metrics = new LoadMetrics();
        metrics.start();
        try {
            List<String> sessionCookies = loginAllMembers();
            connectAllMembers(sessionCookies, metrics);
            subscribeAllMembers(metrics);

            TimeUnit.SECONDS.sleep(HEARTBEAT_OBSERVATION_SECONDS);
            long aliveConnections = sessions.stream().filter(StompSession::isConnected).count();

            boolean allMessagesReceived = broadcastSimultaneously(metrics);
            metrics.finish();
            metrics.log(aliveConnections);

            assertThat(sessions).hasSize(USER_COUNT);
            assertThat(aliveConnections).isEqualTo(USER_COUNT);
            assertThat(allMessagesReceived).isTrue();
            assertThat(metrics.deliveryCount()).isEqualTo(EXPECTED_DELIVERY_COUNT);
            assertThat(metrics.transportErrorCount()).isZero();
        } finally {
            metrics.close();
        }
    }

    private List<String> loginAllMembers() throws Exception {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS))
                        .build();
        try (var executor = Executors.newFixedThreadPool(LOGIN_CONCURRENCY)) {
            List<CompletableFuture<String>> loginFutures = new ArrayList<>(USER_COUNT);
            for (int index = 0; index < USER_COUNT; index++) {
                int memberIndex = index;
                loginFutures.add(
                        CompletableFuture.supplyAsync(
                                () -> login(httpClient, memberIndex), executor));
            }
            CompletableFuture.allOf(loginFutures.toArray(CompletableFuture[]::new))
                    .get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return loginFutures.stream().map(CompletableFuture::join).toList();
        }
    }

    private String login(HttpClient httpClient, int memberIndex) {
        try {
            HttpResponse<String> csrfResponse =
                    httpClient.send(
                            HttpRequest.newBuilder().uri(httpUri("/api/v1/csrf")).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            String csrfCookie = cookie(csrfResponse, "XSRF-TOKEN");
            String csrfToken = objectMapper.readTree(csrfResponse.body()).get("token").asText();
            MemberLoginRequest loginRequest =
                    new MemberLoginRequest(email(memberIndex), PASSWORD, false);
            HttpResponse<String> loginResponse =
                    httpClient.send(
                            HttpRequest.newBuilder()
                                    .uri(httpUri("/api/v1/auth/login"))
                                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                    .header("X-XSRF-TOKEN", csrfToken)
                                    .header(HttpHeaders.COOKIE, csrfCookie)
                                    .POST(
                                            HttpRequest.BodyPublishers.ofString(
                                                    objectMapper.writeValueAsString(loginRequest)))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            if (loginResponse.statusCode() != HttpStatus.OK.value()) {
                throw new IllegalStateException();
            }
            return cookie(loginResponse, "JSESSIONID");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void connectAllMembers(List<String> sessionCookies, LoadMetrics metrics)
            throws Exception {
        List<CompletableFuture<StompSession>> connectionFutures = new ArrayList<>(USER_COUNT);
        for (String sessionCookie : sessionCookies) {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add(HttpHeaders.COOKIE, sessionCookie);
            long startedAt = System.nanoTime();
            connectionFutures.add(
                    stompClient
                            .connectAsync(
                                    webSocketUri().toString(),
                                    headers,
                                    new StompSessionHandlerAdapter() {
                                        @Override
                                        public void handleTransportError(
                                                StompSession session, Throwable exception) {
                                            metrics.recordTransportError();
                                        }
                                    })
                            .thenApply(
                                    session -> {
                                        metrics.recordConnectionLatency(startedAt);
                                        return session;
                                    }));
        }

        CompletableFuture.allOf(connectionFutures.toArray(CompletableFuture[]::new))
                .get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        for (CompletableFuture<StompSession> connectionFuture : connectionFutures) {
            sessions.add(connectionFuture.join());
        }
    }

    private void subscribeAllMembers(LoadMetrics metrics) throws InterruptedException {
        for (StompSession session : sessions) {
            session.subscribe(CHAT_SUBSCRIPTION, metrics.frameHandler());
        }
        TimeUnit.SECONDS.sleep(SUBSCRIPTION_STABILIZATION_SECONDS);
    }

    private boolean broadcastSimultaneously(LoadMetrics metrics) throws Exception {
        CountDownLatch startBarrier = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> sendFutures = new ArrayList<>(USER_COUNT);
            for (int index = 0; index < USER_COUNT; index++) {
                int senderIndex = index;
                StompSession session = sessions.get(index);
                sendFutures.add(
                        CompletableFuture.runAsync(
                                () -> {
                                    await(startBarrier);
                                    String content = message(senderIndex);
                                    metrics.recordMessageSent(content);
                                    session.send(
                                            CHAT_DESTINATION,
                                            new ChatMessageRequest(null, content));
                                },
                                executor));
            }
            startBarrier.countDown();
            CompletableFuture.allOf(sendFutures.toArray(CompletableFuture[]::new))
                    .get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return metrics.awaitDeliveries();
        }
    }

    private URI httpUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private URI webSocketUri() {
        return URI.create("ws://localhost:" + port + "/ws-chat");
    }

    private String cookie(HttpResponse<?> response, String cookieName) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(value -> value.split(";", COOKIE_SPLIT_LIMIT)[0])
                .filter(value -> value.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static String email(int index) {
        return "chat-load-" + index + "@snowthing.org";
    }

    private static String nickname(int index) {
        return "부하회원" + index;
    }

    private static String message(int index) {
        return "load-message-" + index;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class LoadMetrics implements AutoCloseable {

        private final CountDownLatch deliveryLatch = new CountDownLatch(EXPECTED_DELIVERY_COUNT);
        private final ConcurrentLinkedQueue<Long> connectionLatenciesNanos =
                new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Long> deliveryLatenciesNanos =
                new ConcurrentLinkedQueue<>();
        private final Map<String, Long> sentAtNanosByContent = new ConcurrentHashMap<>();
        private final AtomicInteger deliveryCount = new AtomicInteger();
        private final AtomicInteger transportErrorCount = new AtomicInteger();
        private final AtomicLong maxHeapBytes = new AtomicLong();
        private final AtomicLong maxCpuPermille = new AtomicLong();
        private final MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        private final com.sun.management.OperatingSystemMXBean operatingSystemMxBean =
                ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        private final ScheduledExecutorService sampler =
                Executors.newSingleThreadScheduledExecutor();
        private long startedAtNanos;
        private long finishedAtNanos;

        void start() {
            startedAtNanos = System.nanoTime();
            sampler.scheduleAtFixedRate(
                    this::sample, 0, METRIC_SAMPLE_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        }

        void finish() {
            finishedAtNanos = System.nanoTime();
            sample();
        }

        void recordConnectionLatency(long startedAtNanos) {
            connectionLatenciesNanos.add(System.nanoTime() - startedAtNanos);
        }

        void recordMessageSent(String content) {
            sentAtNanosByContent.put(content, System.nanoTime());
        }

        void recordTransportError() {
            transportErrorCount.incrementAndGet();
        }

        StompFrameHandler frameHandler() {
            return new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return ChatMessageResponse.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    ChatMessageResponse response = (ChatMessageResponse) payload;
                    Long sentAtNanos = sentAtNanosByContent.get(response.content());
                    if (sentAtNanos != null) {
                        deliveryLatenciesNanos.add(System.nanoTime() - sentAtNanos);
                    }
                    deliveryCount.incrementAndGet();
                    deliveryLatch.countDown();
                }
            };
        }

        boolean awaitDeliveries() throws InterruptedException {
            return deliveryLatch.await(DELIVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        int deliveryCount() {
            return deliveryCount.get();
        }

        int transportErrorCount() {
            return transportErrorCount.get();
        }

        void log(long aliveConnections) {
            LOGGER.info(
                    "CHAT_LOAD_RESULT users={} alive={} expectedDeliveries={} actualDeliveries={} "
                            + "connectionP95Ms={} deliveryP95Ms={} deliveryP99Ms={} "
                            + "maxHeapMb={} maxProcessCpuPercent={} transportErrors={} elapsedMs={}",
                    USER_COUNT,
                    aliveConnections,
                    EXPECTED_DELIVERY_COUNT,
                    deliveryCount(),
                    percentileMillis(connectionLatenciesNanos, PERCENTILE_95),
                    percentileMillis(deliveryLatenciesNanos, PERCENTILE_95),
                    percentileMillis(deliveryLatenciesNanos, PERCENTILE_99),
                    maxHeapBytes.get() / BYTES_PER_MEBIBYTE,
                    maxCpuPermille.get() / (CPU_PERMILLE_BASE / (double) PERCENT_BASE),
                    transportErrorCount(),
                    TimeUnit.NANOSECONDS.toMillis(finishedAtNanos - startedAtNanos));
        }

        private void sample() {
            maxHeapBytes.accumulateAndGet(memoryMxBean.getHeapMemoryUsage().getUsed(), Math::max);
            double cpuLoad = operatingSystemMxBean.getProcessCpuLoad();
            if (cpuLoad >= 0) {
                maxCpuPermille.accumulateAndGet(Math.round(cpuLoad * CPU_PERMILLE_BASE), Math::max);
            }
        }

        private long percentileMillis(ConcurrentLinkedQueue<Long> samples, int percentile) {
            if (samples.isEmpty()) {
                return 0;
            }
            List<Long> sorted = samples.stream().sorted(Comparator.naturalOrder()).toList();
            int index =
                    Math.min(
                            sorted.size() - 1,
                            (int) Math.ceil(sorted.size() * percentile / (double) PERCENT_BASE)
                                    - 1);
            return TimeUnit.NANOSECONDS.toMillis(sorted.get(index));
        }

        @Override
        public void close() {
            sampler.shutdownNow();
        }
    }
}
