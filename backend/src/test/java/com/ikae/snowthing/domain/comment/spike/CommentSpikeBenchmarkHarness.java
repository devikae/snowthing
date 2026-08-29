package com.ikae.snowthing.domain.comment.spike;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Spike 실험용 성능, 페이로드 크기, SQL 로깅 및 EXPLAIN 분석 자동화 하네스 */
@Slf4j
public class CommentSpikeBenchmarkHarness {

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public record ExplainRow(
            String id,
            String selectType,
            String table,
            String type,
            String possibleKeys,
            String key,
            String keyLen,
            String ref,
            String rows,
            String filtered,
            String extra) {}

    public record ScenarioMetric(
            String scenarioName,
            int queryCount,
            int fetchedRows,
            double elapsedMs,
            int payloadBytes,
            double payloadKb,
            List<String> executedSqls,
            List<List<ExplainRow>> explains) {}

    /** 실행 시간 및 응답 JSON 직렬화 바이트 크기를 측정합니다. */
    public static <T> ScenarioMetric measureScenario(
            String scenarioName,
            int queryCount,
            int fetchedRows,
            Supplier<T> supplier,
            List<String> executedSqls,
            EntityManager em) {

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

        // EXPLAIN 실행
        List<List<ExplainRow>> explains = new ArrayList<>();
        if (em != null && executedSqls != null) {
            for (String sql : executedSqls) {
                try {
                    @SuppressWarnings("unchecked")
                    List<Object[]> rows = em.createNativeQuery("EXPLAIN " + sql).getResultList();
                    List<ExplainRow> explainList = new ArrayList<>();
                    for (Object[] r : rows) {
                        explainList.add(
                                new ExplainRow(
                                        String.valueOf(r[0]),
                                        String.valueOf(r[1]),
                                        String.valueOf(r[2]),
                                        String.valueOf(r[3]),
                                        String.valueOf(r[4]),
                                        String.valueOf(r[5]),
                                        String.valueOf(r[6]),
                                        String.valueOf(r[7]),
                                        String.valueOf(r[8]),
                                        String.valueOf(r[9]),
                                        String.valueOf(r[10])));
                    }
                    explains.add(explainList);
                } catch (Exception e) {
                    log.warn("[EXPLAIN] 실행 불가 또는 무시: {}", e.getMessage());
                }
            }
        }

        log.info("==========================================================");
        log.info("[Spike Benchmark: {}]", scenarioName);
        log.info(" - 쿼리 수: {} 회 | 읽은 행: {} 행", queryCount, fetchedRows);
        log.info(" - 실행 시간: {} ms", String.format("%.3f", elapsedMs));
        log.info(" - 직렬화 크기: {} Bytes ({} KB)", payloadBytes, String.format("%.2f", payloadKb));
        log.info("==========================================================");

        return new ScenarioMetric(
                scenarioName,
                queryCount,
                fetchedRows,
                elapsedMs,
                payloadBytes,
                payloadKb,
                executedSqls,
                explains);
    }

    /**
     * 측정된 모든 시나리오 결과를 docs/study/sprint03/comment/test/spike_result_candidate_{candidateNumber}.md
     * 마크다운 파일로 자동 생성/저장합니다.
     */
    public static void generateAndSaveReport(
            int candidateNumber,
            String candidateName,
            String branchName,
            String implementationSummary,
            List<ScenarioMetric> metrics,
            String issuesAndBottlenecks,
            String evaluation) {

        StringBuilder sb = new StringBuilder();
        sb.append("# [Spike 결과 보고서] 후보 ")
                .append(candidateNumber)
                .append(": ")
                .append(candidateName)
                .append("\n\n");
        sb.append("- **브랜치명**: `").append(branchName).append("`\n");
        sb.append("- **측정 일시**: ").append(LocalDate.now()).append("\n");
        sb.append("- **작성자**: devikae (자동 생성)\n\n");
        sb.append("---\n\n");

        sb.append("## 1. 구현 요약 (PoC Implementation)\n");
        sb.append(implementationSummary).append("\n\n");
        sb.append("---\n\n");

        sb.append("## 2. 측정 결과 데이터 매트릭스\n\n");
        sb.append(
                "| 시나리오 | 쿼리 수 (Count) | 읽은 Row 수 (Rows) | 응답 크기 (Bytes / KB) | 실행 시간 (Elapsed ms) |\n");
        sb.append("| :--- | :---: | :---: | :---: | :---: |\n");

        for (ScenarioMetric m : metrics) {
            sb.append("| **")
                    .append(m.scenarioName())
                    .append("** | ")
                    .append(m.queryCount())
                    .append("회 | ")
                    .append(m.fetchedRows())
                    .append("행 | ")
                    .append(m.payloadBytes())
                    .append(" B (")
                    .append(String.format("%.2f", m.payloadKb()))
                    .append(" KB) | ")
                    .append(String.format("%.3f", m.elapsedMs()))
                    .append(" ms |\n");
        }
        sb.append("\n---\n\n");

        sb.append("## 3. 실행된 실제 SQL 및 MySQL EXPLAIN\n\n");
        int sIdx = 1;
        for (ScenarioMetric m : metrics) {
            sb.append("### ").append(sIdx++).append(") ").append(m.scenarioName()).append("\n\n");
            if (m.executedSqls() != null && !m.executedSqls().isEmpty()) {
                for (int q = 0; q < m.executedSqls().size(); q++) {
                    sb.append("#### [Query ").append(q + 1).append("]\n");
                    sb.append("```sql\n").append(m.executedSqls().get(q)).append("\n```\n\n");

                    if (m.explains() != null && m.explains().size() > q) {
                        List<ExplainRow> exList = m.explains().get(q);
                        sb.append("**EXPLAIN 분석**:\n\n");
                        sb.append("| table | type | key | rows | Extra |\n");
                        sb.append("| :--- | :--- | :--- | :--- | :--- |\n");
                        for (ExplainRow ex : exList) {
                            sb.append("| ")
                                    .append(ex.table())
                                    .append(" | ")
                                    .append(ex.type())
                                    .append(" | ")
                                    .append(ex.key())
                                    .append(" | ")
                                    .append(ex.rows())
                                    .append(" | ")
                                    .append(ex.extra())
                                    .append(" |\n");
                        }
                        sb.append("\n");
                    }
                }
            }
        }
        sb.append("---\n\n");

        sb.append("## 4. 발견된 결함 및 한계점 (Issues & Bottlenecks)\n");
        sb.append(issuesAndBottlenecks).append("\n\n");
        sb.append("---\n\n");

        sb.append("## 5. 최종 평가 및 소견\n");
        sb.append(evaluation).append("\n");

        try {
            Path targetDir = Paths.get("..", "docs", "study", "sprint03", "comment", "test");
            if (!Files.exists(targetDir)) {
                targetDir = Paths.get("docs", "study", "sprint03", "comment", "test");
            }
            Files.createDirectories(targetDir);

            Path targetFile =
                    targetDir.resolve("spike_result_candidate_" + candidateNumber + ".md");
            Files.writeString(targetFile, sb.toString());
            log.info("[Spike Report Generated] -> {}", targetFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("[Spike Report] 파일 저장 실패", e);
        }
    }
}
