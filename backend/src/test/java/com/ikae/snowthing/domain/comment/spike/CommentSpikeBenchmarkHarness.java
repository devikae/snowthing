package com.ikae.snowthing.domain.comment.spike;

import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Spike 실험용 성능 및 페이로드 크기 측정 유틸리티 */
@Slf4j
public class CommentSpikeBenchmarkHarness {

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public record BenchmarkResult<T>(
            T result, long elapsedNanos, double elapsedMs, int payloadBytes, double payloadKb) {}

    /** 실행 시간 및 응답 JSON 바이트 크기를 일관되게 측정합니다. */
    public static <T> BenchmarkResult<T> measure(String testName, Supplier<T> supplier) {
        long start = System.nanoTime();
        T result = supplier.get();
        long end = System.nanoTime();

        long elapsedNanos = end - start;
        double elapsedMs = elapsedNanos / 1_000_000.0;

        int payloadBytes = 0;
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            payloadBytes = bytes.length;
        } catch (Exception e) {
            log.error("[Benchmark] 직렬화 실패", e);
        }

        double payloadKb = payloadBytes / 1024.0;

        log.info("==========================================================");
        log.info("[Spike Benchmark: {}]", testName);
        log.info(" - 실행 시간: {} ms ({} ns)", String.format("%.3f", elapsedMs), elapsedNanos);
        log.info(" - 직렬화 크기: {} Bytes ({} KB)", payloadBytes, String.format("%.2f", payloadKb));
        log.info("==========================================================");

        return new BenchmarkResult<>(result, elapsedNanos, elapsedMs, payloadBytes, payloadKb);
    }
}
