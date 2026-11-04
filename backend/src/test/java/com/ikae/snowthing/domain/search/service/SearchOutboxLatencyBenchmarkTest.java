package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.PostCreateRequest;
import com.ikae.snowthing.domain.post.dto.PostUpdateRequest;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;

/**
 * 로컬 반영 지연 측정 (LOCAL 전용, 운영 수치가 아니다). 기본 테스트에서 빠지고 {@code -PincludeBenchmark}로만 돈다.
 *
 * <p>스케줄러를 켠 상태에서 게시글 수정 N건을 무작위 간격(0~300ms)으로 커밋하고, outbox 행의 created_at(원문 트랜잭션 안에서 기록)과
 * processed_at(색인 반영 트랜잭션 안에서 기록)의 차이를 지연으로 본다. 두 시각 모두 커밋 직전에 찍히므로 실제 커밋 간격과는 커밋 소요 시간만큼 다르다.
 *
 * <p>환경 변수: SEARCH_LATENCY_N(기본 100), SEARCH_LATENCY_POLL_MS(기본 1000). 결과는 build/benchmark/에 JSON으로
 * 남긴다.
 */
@Tag("benchmark")
@SpringBootTest(
        properties = {
            "snowthing.search.indexer.enabled=true",
            "snowthing.search.indexer.initial-delay-ms=0",
            "snowthing.search.indexer.poll-interval-ms=${SEARCH_LATENCY_POLL_MS:1000}"
        })
class SearchOutboxLatencyBenchmarkTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;
    @Autowired private PostService postService;
    @Autowired private ObjectMapper objectMapper;

    @Value("${snowthing.search.indexer.poll-interval-ms}")
    private long pollIntervalMs;

    @Value("${SEARCH_LATENCY_N:100}")
    private int changes;

    private Long memberId;
    private Long categoryId;

    @AfterEach
    void tearDown() {
        transactions.executeWithoutResult(
                status -> {
                    for (Long id :
                            jdbc.queryForList(
                                    "SELECT post_id FROM post WHERE category_id = ?",
                                    Long.class,
                                    categoryId)) {
                        jdbc.update(
                                "DELETE FROM post_content_image_reference WHERE post_id = ?", id);
                        jdbc.update("DELETE FROM post WHERE post_id = ?", id);
                    }
                    jdbc.update("DELETE FROM post_category WHERE category_id = ?", categoryId);
                    jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
                    jdbc.update("DELETE FROM search_outbox");
                    jdbc.update("DELETE FROM search_index_state");
                    jdbc.update("DELETE FROM search_document");
                });
    }

    @Test
    @DisplayName("LOCAL: 원문 커밋부터 색인 반영까지 지연 P50/P95")
    void measureCommitToIndexedLatency() throws Exception {
        String code =
                "LAT_"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 10)
                                .toUpperCase(Locale.ROOT);
        Member writer =
                transactions.execute(
                        status -> {
                            Member member =
                                    members.save(
                                            Member.builder()
                                                    .publicId(UUID.randomUUID().toString())
                                                    .email(UUID.randomUUID() + "@test.invalid")
                                                    .nickname("lat" + code.substring(4, 13))
                                                    .role(Role.ROLE_USER)
                                                    .build());
                            categories.save(
                                    PostCategory.builder().code(code).name("지연 측정").build());
                            return member;
                        });
        memberId = writer.getId();
        categoryId = categories.findByCode(code).orElseThrow().getId();
        CustomUserDetails details = new CustomUserDetails(writer);

        String publicId =
                postService
                        .createPost(
                                new PostCreateRequest(
                                        code, "지연 측정 글", "본문 0", false, null, List.of()),
                                details,
                                "127.0.0.1")
                        .publicId();
        waitUntilDrained(Duration.ofSeconds(30));
        jdbc.update("DELETE FROM search_outbox");

        Random random = new Random(20261104L);
        for (int i = 1; i <= changes; i++) {
            Thread.sleep(random.nextInt(301));
            postService.updatePost(
                    publicId,
                    new PostUpdateRequest(code, "지연 측정 글", "바인딩 각도 본문 " + i, null, null),
                    details);
        }
        waitUntilDrained(Duration.ofSeconds(60 + changes));

        List<Long> delays =
                jdbc.queryForList(
                        "SELECT TIMESTAMPDIFF(MICROSECOND, created_at, processed_at) FROM search_outbox"
                                + " WHERE status = 'DONE' ORDER BY event_id",
                        Long.class);
        assertThat(delays).hasSize(changes);
        List<Long> sorted = new ArrayList<>(delays);
        sorted.sort(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", "LOCAL measurement, not production");
        result.put("measuredAt", LocalDateTime.now().toString());
        result.put("n", changes);
        result.put("pollIntervalMs", pollIntervalMs);
        result.put("changeSpacingMs", "uniform random 0-300 (seed 20261104)");
        result.put("delayDefinition", "search_outbox.processed_at - created_at");
        result.put("p50Ms", percentile(sorted, 50) / 1000.0);
        result.put("p95Ms", percentile(sorted, 95) / 1000.0);
        result.put("maxMs", sorted.get(sorted.size() - 1) / 1000.0);
        result.put("minMs", sorted.get(0) / 1000.0);
        result.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        result.put("cpus", Runtime.getRuntime().availableProcessors());
        result.put("java", System.getProperty("java.version"));
        result.put("mysql", jdbc.queryForObject("SELECT VERSION()", String.class));

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println("SEARCH_OUTBOX_LATENCY " + json);
        write(json);
    }

    private void waitUntilDrained(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer pending =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM search_outbox WHERE status = 'PENDING'",
                            Integer.class);
            if (pending != null && pending == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("outbox not drained in " + timeout);
    }

    /** nearest-rank 백분위수 */
    private static long percentile(List<Long> sorted, int p) {
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }

    private void write(String json) throws IOException {
        Path dir = Path.of("build", "benchmark");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("search-outbox-latency-" + pollIntervalMs + "ms.json"),
                json,
                StandardCharsets.UTF_8);
    }
}
