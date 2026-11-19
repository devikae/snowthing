package com.ikae.snowthing.domain.dmmail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.service.DirectMessageService;
import com.ikae.snowthing.domain.notification.service.NotificationSettingService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.testsupport.FakeSesServer.Mode;

/**
 * 쪽지 메일 동기 기준선과 Outbox 비교 측정 (LOCAL 전용, 운영 수치가 아니다).
 *
 * <p>같은 조건에서 쪽지 보내기 서비스 호출 지연을 잰다. 가짜 SES 응답 지연, 요청 수, 동시 실행 수를 고정한다. 받는 사람은 쪽지마다 다르게 해 쿨다운 묶음이
 * 끼어들지 않게 한다. Outbox 쪽은 워커를 켜 두고 발송 요청 생성부터 SES 수락 기록까지의 지연도 잰다.
 *
 * <p>환경 변수: DM_BENCH_N(기본 60), DM_BENCH_CONCURRENCY(기본 6), DM_BENCH_SES_DELAY_MS(기본 1000). 결과는
 * build/benchmark/dm-mail-*.json에 남긴다. 테스트 DB 커넥션 풀은 5개다.
 */
abstract class DmMailBenchmarkSupport {

    static final int N = intEnv("DM_BENCH_N", 60);
    static final int CONCURRENCY = intEnv("DM_BENCH_CONCURRENCY", 6);
    static final int SES_DELAY_MS = intEnv("DM_BENCH_SES_DELAY_MS", 1000);

    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired MemberRepository members;
    @Autowired DirectMessageService directMessages;
    @Autowired NotificationSettingService settings;
    @Autowired ObjectMapper objectMapper;

    final List<Long> memberIds = new ArrayList<>();

    Map<String, Object> measureSend(String mode) throws Exception {
        jdbc.update("DELETE FROM dm_mail_outbox");
        jdbc.update("DELETE FROM dm_mail_recipient_state");
        DmMailTestConfig.FAKE_SES.reset(Mode.ACCEPT);
        DmMailTestConfig.FAKE_SES.acceptDelay(Duration.ofMillis(SES_DELAY_MS));
        Member sender = member("bsend");
        List<Member> receivers = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Member receiver = member("brecv");
            settings.update(receiver.getId(), true);
            receivers.add(receiver);
        }

        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        long wallStart = System.nanoTime();
        for (Member receiver : receivers) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                long begin = System.nanoTime();
                                try {
                                    directMessages.send(
                                            new CustomUserDetails(sender),
                                            new DirectMessageSendRequest(
                                                    receiver.getPublicId(), "측정", null));
                                    latenciesMs.add((System.nanoTime() - begin) / 1_000_000);
                                } catch (RuntimeException failure) {
                                    errors.add(failure.getClass().getSimpleName());
                                }
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.MINUTES);
        }
        long wallMs = (System.nanoTime() - wallStart) / 1_000_000;
        pool.shutdown();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("measuredAt", LocalDateTime.now().toString());
        result.put("requests", N);
        result.put("concurrency", CONCURRENCY);
        result.put("sesDelayMs", SES_DELAY_MS);
        result.put("dbPoolSize", 5);
        result.put("errors", errors.size());
        result.put("errorTypes", errors.stream().distinct().toList());
        result.put("sendWallMs", wallMs);
        result.put("sendLatencyMs", percentiles(latenciesMs));
        return result;
    }

    /** 끝나지 않은 발송 요청이 없을 때까지 기다린 뒤 생성부터 수락 기록까지의 지연을 잰다. */
    Map<String, Object> measureDelivery(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        long maxBacklog = 0;
        while (System.nanoTime() < deadline) {
            Long open =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM dm_mail_outbox"
                                    + " WHERE status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING')",
                            Long.class);
            maxBacklog = Math.max(maxBacklog, open == null ? 0 : open);
            if (open != null && open == 0) {
                break;
            }
            Thread.sleep(100);
        }
        List<Long> deliveryMs =
                jdbc.queryForList(
                        "SELECT TIMESTAMPDIFF(MICROSECOND, created_at, updated_at) DIV 1000"
                                + " FROM dm_mail_outbox WHERE status = 'ACCEPTED'",
                        Long.class);
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("accepted", deliveryMs.size());
        delivery.put(
                "statusCounts",
                jdbc.queryForList(
                        "SELECT status, COUNT(*) AS cnt FROM dm_mail_outbox GROUP BY status"));
        delivery.put("maxBacklogObserved", maxBacklog);
        delivery.put("enqueueToAcceptedMs", percentiles(deliveryMs));
        delivery.put("fakeSesRequests", DmMailTestConfig.FAKE_SES.requests());
        delivery.put("fakeSesAccepted", DmMailTestConfig.FAKE_SES.accepted());
        return delivery;
    }

    void write(String name, Map<String, Object> result) throws IOException {
        Path dir = Path.of("build", "benchmark");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve(name + ".json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result),
                StandardCharsets.UTF_8);
        System.out.println("DM_MAIL_BENCH " + name + " " + objectMapper.writeValueAsString(result));
    }

    void cleanup() {
        jdbc.update("DELETE FROM dm_mail_outbox");
        jdbc.update("DELETE FROM dm_mail_recipient_state");
        for (Long id : memberIds) {
            jdbc.update(
                    "DELETE FROM direct_message WHERE sender_id = ? OR receiver_id = ?", id, id);
            jdbc.update("DELETE FROM member_notification_setting WHERE member_id = ?", id);
        }
        for (Long id : memberIds) {
            jdbc.update("DELETE FROM member WHERE member_id = ?", id);
        }
        memberIds.clear();
    }

    private Member member(String tag) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                transactions.execute(
                        status ->
                                members.save(
                                        Member.builder()
                                                .email(tag + unique + "@bench.snowthing.org")
                                                .password("encoded")
                                                .nickname(tag + unique)
                                                .emailVerifiedAt(LocalDateTime.now())
                                                .role(Role.ROLE_USER)
                                                .status(MemberStatus.ACTIVE)
                                                .build()));
        memberIds.add(saved.getId());
        return saved;
    }

    static Map<String, Object> percentiles(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("count", sorted.size());
        if (sorted.isEmpty()) {
            return p;
        }
        p.put("p50", at(sorted, 0.50));
        p.put("p95", at(sorted, 0.95));
        p.put("max", sorted.get(sorted.size() - 1));
        p.put("min", sorted.get(0));
        return p;
    }

    private static long at(List<Long> sorted, double q) {
        int index = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }
}
