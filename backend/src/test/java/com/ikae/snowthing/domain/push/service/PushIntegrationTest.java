package com.ikae.snowthing.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberWithdrawalRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.push.dto.PushSubscriptionRequest;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 키가 있는 상태에서 구독 API, 댓글 알림 뒤 푸시 발송, 만료 구독 정리를 실제 MySQL과 커밋으로 본다. 푸시 서비스로 나가는 HTTP만 대체한다.
 *
 * <p>AFTER_COMMIT 리스너는 테스트 트랜잭션 안에서는 불리지 않으므로 클래스에 {@code @Transactional}을 두지 않고 직접 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PushIntegrationTest {

    private static final String IP = "127.0.0.1";
    private static final KeyPair VAPID = WebPushCrypto.generateKeyPair();
    private static final String VAPID_PUBLIC = VapidKeysTest.publicOf(VAPID);

    @DynamicPropertySource
    static void webPush(DynamicPropertyRegistry registry) {
        registry.add("snowthing.web-push.public-key", () -> VAPID_PUBLIC);
        registry.add("snowthing.web-push.private-key", () -> VapidKeysTest.privateOf(VAPID));
        registry.add("snowthing.web-push.subject", () -> "mailto:ops@example.com");
    }

    @MockitoBean private PushHttpClient pushHttpClient;

    @Autowired private MockMvc mockMvc;
    @Autowired private PushSubscriptionService subscriptionService;
    @Autowired private PostService postService;
    @Autowired private CommentService commentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberWithdrawalRepository withdrawalRepository;
    @Autowired private PostCategoryRepository categoryRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<String> postIds = new ArrayList<>();

    private CustomUserDetails author;
    private CustomUserDetails commenter;

    @BeforeEach
    void setUp() {
        categoryRepository
                .findByCode("FREE")
                .orElseGet(() -> categoryRepository.save(new PostCategory("자유게시판", "FREE")));
        author = new CustomUserDetails(saveMember("푸시글쓴이"));
        commenter = new CustomUserDetails(saveMember("푸시댓글러"));
    }

    @AfterEach
    void tearDown() {
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM push_subscription WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM notification WHERE receiver_id = ?", memberId);
        }
        for (String publicId : postIds) {
            Long postId =
                    jdbc.queryForObject(
                            "SELECT post_id FROM post WHERE public_id = ?", Long.class, publicId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post_content_image_reference WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("설정 API는 켜짐과 VAPID 공개키를 준다. 비로그인은 401이다")
    void configReturnsPublicKey() throws Exception {
        mockMvc.perform(get("/api/v1/push/config").with(user(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicKey").value(VAPID_PUBLIC));
        mockMvc.perform(get("/api/v1/push/config").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        put("/api/v1/push/subscriptions")
                                .with(csrf())
                                .with(anonymous())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(endpoint("anon"), browserKeys())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 기기를 다른 계정이 다시 등록하면 행은 하나이고 회원이 바뀐다")
    void resubscribeMovesDeviceToCurrentMember() throws Exception {
        String endpoint = endpoint("shared-device");
        BrowserKeys keys = browserKeys();
        subscribe(author, endpoint, keys).andExpect(status().isNoContent());
        subscribe(commenter, endpoint, keys).andExpect(status().isNoContent());

        assertThat(ownerOf(endpoint)).containsExactly(commenter.getMember().getId());
    }

    @Test
    @DisplayName("알려진 푸시 서비스가 아닌 주소나 틀린 키는 400이다")
    void rejectsInvalidSubscription() throws Exception {
        BrowserKeys keys = browserKeys();
        subscribe(author, "https://169.254.169.254/latest/meta-data", keys)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PUSH_002"));
        subscribe(author, endpoint("bad-key"), new BrowserKeys(null, "AAAA", keys.auth()))
                .andExpect(status().isBadRequest());
        subscribe(author, endpoint("bad-auth"), new BrowserKeys(null, keys.p256dh(), "AAAA"))
                .andExpect(status().isBadRequest());
        assertThat(countOf(author)).isZero();
    }

    @Test
    @DisplayName("회원당 최근 10개 기기만 남긴다")
    void keepsRecentTenDevices() throws Exception {
        BrowserKeys keys = browserKeys();
        for (int i = 0; i < 11; i++) {
            subscribe(author, endpoint("device-" + i), keys).andExpect(status().isNoContent());
        }
        assertThat(countOf(author)).isEqualTo(10);
        assertThat(ownerOf(endpoint("device-0"))).isEmpty();
        assertThat(ownerOf(endpoint("device-10"))).hasSize(1);
    }

    @Test
    @DisplayName("해지는 내 구독만 지운다")
    void unsubscribeOnlyMine() throws Exception {
        String endpoint = endpoint("mine");
        subscribe(author, endpoint, browserKeys()).andExpect(status().isNoContent());

        unsubscribe(commenter, endpoint).andExpect(status().isNoContent());
        assertThat(ownerOf(endpoint)).hasSize(1);

        unsubscribe(author, endpoint).andExpect(status().isNoContent());
        assertThat(ownerOf(endpoint)).isEmpty();
    }

    @Test
    @DisplayName("같은 기기를 동시에 두 번 등록해도 행은 하나다")
    void concurrentSubscribeKeepsOneRow() throws Exception {
        String endpoint = endpoint("race");
        BrowserKeys keys = browserKeys();
        PushSubscriptionRequest request =
                new PushSubscriptionRequest(
                        endpoint, new PushSubscriptionRequest.Keys(keys.p256dh(), keys.auth()));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (CustomUserDetails member : List.of(author, commenter)) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    subscriptionService.subscribe(member, request);
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(ownerOf(endpoint)).hasSize(1);
    }

    @Test
    @DisplayName("댓글 알림이 커밋되면 글쓴이 기기로 암호화된 푸시가 가고 본문에 댓글 내용은 없다")
    void commentTriggersEncryptedPush() throws Exception {
        String endpoint = endpoint("author-phone");
        BrowserKeys keys = browserKeys();
        subscribe(author, endpoint, keys).andExpect(status().isNoContent());
        LocalDateTime before = lastUsedAt(endpoint);
        when(pushHttpClient.post(any(), anyMap(), any())).thenReturn(201);
        String post = createPost();

        comment(post, "비밀 이야기가 담긴 댓글");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(pushHttpClient, timeout(5_000))
                .post(eq(URI.create(endpoint)), headers.capture(), body.capture());
        assertThat(headers.getValue())
                .containsEntry("Content-Encoding", "aes128gcm")
                .containsEntry("TTL", "86400");
        assertThat(headers.getValue().get("Authorization"))
                .startsWith("vapid t=")
                .endsWith("k=" + VAPID_PUBLIC);
        String payload =
                new String(
                        WebPushTestDecryptor.decrypt(
                                body.getValue(),
                                keys.pair(),
                                WebPushCrypto.base64UrlDecode(keys.auth())),
                        StandardCharsets.UTF_8);
        assertThat(payload)
                .contains("\"title\":\"눈팅\"")
                .contains("내 글에 새 댓글이 달렸어요")
                .contains("\"url\":\"/posts/" + post + "#comments\"")
                .doesNotContain("비밀")
                .doesNotContain(commenter.getNickname());
        waitUntil(() -> lastUsedAt(endpoint).isAfter(before));
    }

    @Test
    @DisplayName("푸시 서비스가 410으로 답하면 그 구독을 지운다")
    void goneSubscriptionIsDeleted() throws Exception {
        String endpoint = endpoint("expired");
        subscribe(author, endpoint, browserKeys()).andExpect(status().isNoContent());
        when(pushHttpClient.post(any(), anyMap(), any())).thenReturn(410);

        comment(createPost(), "댓글");

        waitUntil(() -> ownerOf(endpoint).isEmpty());
    }

    @Test
    @DisplayName("푸시 서비스가 500이거나 연결이 실패해도 구독과 알림은 그대로다")
    void transientFailureKeepsSubscription() throws Exception {
        String endpoint = endpoint("flaky");
        subscribe(author, endpoint, browserKeys()).andExpect(status().isNoContent());
        when(pushHttpClient.post(any(), anyMap(), any()))
                .thenThrow(new IOException("connection reset"));

        comment(createPost(), "댓글");

        verify(pushHttpClient, timeout(5_000)).post(any(), anyMap(), any());
        assertThat(ownerOf(endpoint)).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM notification WHERE receiver_id = ?",
                                Long.class,
                                author.getMember().getId()))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("회원 탈퇴는 그 회원의 구독을 지운다")
    void withdrawalDeletesSubscriptions() throws Exception {
        subscribe(author, endpoint("leaving"), browserKeys()).andExpect(status().isNoContent());
        Member member = author.getMember();

        transactionTemplate.executeWithoutResult(
                status ->
                        withdrawalRepository.erasePersonalData(
                                member.getId(),
                                member.getNickname(),
                                member.getEmail(),
                                List.of(member.getEmail()),
                                "withdrawn-" + member.getId() + "@withdrawn.invalid",
                                LocalDateTime.now()));

        assertThat(countOf(author)).isZero();
    }

    private ResultActions subscribe(CustomUserDetails member, String endpoint, BrowserKeys keys)
            throws Exception {
        return mockMvc.perform(
                put("/api/v1/push/subscriptions")
                        .with(csrf())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(endpoint, keys)));
    }

    private ResultActions unsubscribe(CustomUserDetails member, String endpoint) throws Exception {
        return mockMvc.perform(
                delete("/api/v1/push/subscriptions")
                        .with(csrf())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + endpoint + "\"}"));
    }

    private static String json(String endpoint, BrowserKeys keys) {
        return "{\"endpoint\":\""
                + endpoint
                + "\",\"expirationTime\":null,\"keys\":{\"p256dh\":\""
                + keys.p256dh()
                + "\",\"auth\":\""
                + keys.auth()
                + "\"}}";
    }

    private static String endpoint(String device) {
        return "https://fcm.googleapis.com/fcm/send/test-" + device + "-" + RUN;
    }

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);

    record BrowserKeys(KeyPair pair, String p256dh, String auth) {}

    private static BrowserKeys browserKeys() {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return new BrowserKeys(
                pair,
                WebPushCrypto.base64UrlEncode(
                        WebPushCrypto.encodePublic((ECPublicKey) pair.getPublic())),
                WebPushCrypto.base64UrlEncode(auth));
    }

    private List<Long> ownerOf(String endpoint) {
        return jdbc.queryForList(
                "SELECT member_id FROM push_subscription WHERE endpoint_hash = ?",
                Long.class,
                PushEndpointPolicy.hash(endpoint));
    }

    private LocalDateTime lastUsedAt(String endpoint) {
        return jdbc.queryForObject(
                "SELECT last_used_at FROM push_subscription WHERE endpoint_hash = ?",
                LocalDateTime.class,
                PushEndpointPolicy.hash(endpoint));
    }

    private long countOf(CustomUserDetails member) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM push_subscription WHERE member_id = ?",
                Long.class,
                member.getMember().getId());
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("5초 안에 조건이 맞지 않았습니다.");
            }
            Thread.sleep(50);
        }
    }

    private Member saveMember(String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                memberRepository.save(
                        Member.builder()
                                .email("push-" + suffix + "@example.com")
                                .password(passwordEncoder.encode("Password123!"))
                                .nickname(label + "-" + suffix)
                                .role(Role.ROLE_USER)
                                .status(MemberStatus.ACTIVE)
                                .build());
        memberIds.add(member.getId());
        return member;
    }

    private String createPost() {
        String publicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        "FREE", "푸시 테스트", "본문", false, null, List.of()),
                                author,
                                IP)
                        .publicId();
        postIds.add(publicId);
        return publicId;
    }

    private void comment(String postPublicId, String content) {
        commentService.createComment(
                postPublicId, new CommentCreateRequest(null, content, false, null), commenter, IP);
    }
}
