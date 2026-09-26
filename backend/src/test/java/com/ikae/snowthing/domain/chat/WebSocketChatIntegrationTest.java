package com.ikae.snowthing.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.chat.dto.ChatErrorResponse;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.model.ResortTag;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.MemberService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketChatIntegrationTest {

    @DynamicPropertySource
    static void useRealMySql(DynamicPropertyRegistry registry) {
        String testDbUrl = System.getenv("SNOWTHING_TEST_DB_URL");
        if (testDbUrl == null || testDbUrl.isBlank()) {
            testDbUrl =
                    "jdbc:mysql://localhost:3306/snowthing_test?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Seoul&useAffectedRows=true";
        }
        String user = System.getenv("SNOWTHING_TEST_DB_USERNAME");
        if (user == null || user.isBlank()) {
            user = "snowuser";
        }
        String pass = System.getenv("SNOWTHING_TEST_DB_PASSWORD");
        if (pass == null || pass.isBlank()) {
            pass = "snowthing_pass_2026!";
        }

        final String url = testDbUrl;
        final String u = user;
        final String p = pass;

        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> u);
        registry.add("spring.datasource.password", () -> p);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @LocalServerPort private int port;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private MemberService memberService;

    @Autowired private MemberRepository memberRepository;

    @Autowired private ChatService chatService;

    private WebSocketStompClient stompClient;
    private String sessionCookie;

    @BeforeEach
    void setUp() throws Exception {
        chatService.clearCache();
        memberRepository.deleteAll();

        // 1. 회원가입 및 로그인하여 JSESSIONID 획득
        MemberSignUpRequest signUpRequest =
                MemberSignUpRequest.builder()
                        .email("chattester@snowthing.org")
                        .password("Password123!")
                        .nickname("슬로프장인")
                        .build();
        memberService.signUp(signUpRequest);

        MemberLoginRequest loginRequest =
                new MemberLoginRequest("chattester@snowthing.org", "Password123!", false);
        HttpClient httpClient = HttpClient.newHttpClient();

        // 1-1. CSRF 토큰 획득
        HttpRequest csrfReq =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/v1/csrf"))
                        .GET()
                        .build();
        HttpResponse<String> csrfRes =
                httpClient.send(csrfReq, HttpResponse.BodyHandlers.ofString());
        String xsrfCookie = csrfRes.headers().firstValue("Set-Cookie").orElseThrow();
        String xsrfToken = objectMapper.readTree(csrfRes.body()).get("token").asText();

        // 1-2. 로그인 요청
        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", xsrfToken)
                        .header("Cookie", xsrfCookie.split(";")[0])
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        objectMapper.writeValueAsString(loginRequest)))
                        .build();

        HttpResponse<String> loginResponse =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(loginResponse.statusCode()).isEqualTo(200);

        String setCookie = loginResponse.headers().firstValue("Set-Cookie").orElseThrow();
        sessionCookie = setCookie.split(";")[0];

        // 2. STOMP 클라이언트 초기화
        stompClient =
                new WebSocketStompClient(
                        new SockJsClient(
                                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("STOMP 연결, /sub/chat/main 채널 구독 및 리조트 제보 브로드캐스트 수신 성공")
    void sendAndReceiveResortChatMessage_Success() throws Exception {
        // given
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        String wsUrl = "http://localhost:" + port + "/ws-chat";
        CompletableFuture<ChatMessageResponse> messageFuture = new CompletableFuture<>();

        StompSession session =
                stompClient
                        .connectAsync(wsUrl, wsHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS);

        assertThat(session.isConnected()).isTrue();

        // when: /sub/chat/main 구독
        session.subscribe(
                "/sub/chat/main",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ChatMessageResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messageFuture.complete((ChatMessageResponse) payload);
                    }
                });

        // and: /pub/chat/messages 전송
        ChatMessageRequest request = new ChatMessageRequest("PHOENIX", "챔피언 상단 엣지 완벽하게 박힙니다!");
        session.send("/pub/chat/messages", request);

        // then: 브로드캐스트 수신 검증
        ChatMessageResponse response = messageFuture.get(5, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.sender().nickname()).isEqualTo("슬로프장인");
        assertThat(response.resortTag()).isEqualTo(ResortTag.PHOENIX.name());
        assertThat(response.content()).isEqualTo("챔피언 상단 엣지 완벽하게 박힙니다!");
    }

    @Test
    @DisplayName("외부 링크 전송 시 발신자 단독 에러 채널(/user/queue/errors)로 CHAT_001 수신")
    void sendExternalLink_ReturnsPrivateError() throws Exception {
        // given
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        String wsUrl = "http://localhost:" + port + "/ws-chat";
        CompletableFuture<ChatErrorResponse> errorFuture = new CompletableFuture<>();

        StompSession session =
                stompClient
                        .connectAsync(wsUrl, wsHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS);

        // when: /user/queue/errors 구독
        session.subscribe(
                "/user/queue/errors",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ChatErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errorFuture.complete((ChatErrorResponse) payload);
                    }
                });

        // and: 외부 링크 발송
        ChatMessageRequest forbiddenRequest =
                new ChatMessageRequest(null, "여기 데크 공구 사이트입니다 https://illegal-shop.com");
        session.send("/pub/chat/messages", forbiddenRequest);

        // then: 개인 에러 수신 검증
        ChatErrorResponse error = errorFuture.get(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("CHAT_001");
        assertThat(error.message()).contains("외부 링크 및 메신저 연락처는 전송할 수 없습니다.");
    }

    @Test
    @DisplayName("공백 메시지 전송 시 발신자 단독 에러 채널로 CHAT_004를 수신한다")
    void sendBlankMessage_returnsPrivateValidationError() throws Exception {
        WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
        wsHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        String wsUrl = "http://localhost:" + port + "/ws-chat";
        CompletableFuture<ChatErrorResponse> errorFuture = new CompletableFuture<>();

        StompSession session =
                stompClient
                        .connectAsync(wsUrl, wsHeaders, new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS);
        session.subscribe(
                "/user/queue/errors",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return ChatErrorResponse.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        errorFuture.complete((ChatErrorResponse) payload);
                    }
                });

        session.send("/pub/chat/messages", new ChatMessageRequest(null, "   "));

        ChatErrorResponse error = errorFuture.get(5, TimeUnit.SECONDS);
        assertThat(error.code()).isEqualTo("CHAT_004");
        assertThat(error.message()).contains("공백 메시지");
    }
}
