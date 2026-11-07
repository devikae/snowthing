package com.ikae.snowthing.global.privacy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.util.WriterDisplayFormatter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 처리방침의 IP 보관 기간을 지킨다. 작성 후 {@value #RETENTION_MONTHS}개월이 지난 글과 댓글의 작성 IP는 앞 두 자리만 남기고, 추천 IP는 지운다.
 *
 * <p>글과 댓글의 writer_ip는 NOT NULL이고 익명 표시에 앞 두 자리를 쓴다. 표시 값을 그대로 저장해 지난 글의 익명 표시가 바뀌지 않게 한다. IPv6는 표시
 * 값이 전체 주소에서 계산되므로 SQL 문자열 자르기로는 같은 값을 만들 수 없어 자바에서 변환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriterIpRetentionScheduler {

    static final int RETENTION_MONTHS = 3;
    static final int BATCH_SIZE = 1_000;
    static final int MAX_BATCHES = 200;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Scheduled(cron = "${snowthing.writer-ip.cleanup-cron:0 50 4 * * *}", zone = "Asia/Seoul")
    public void purgeExpired() {
        try {
            LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
            log.info("보관 기간이 지난 IP 정리 {}건", purge(now.minusMonths(RETENTION_MONTHS)));
        } catch (RuntimeException e) {
            log.warn("보관 기간이 지난 IP 정리 실패: {}", e.getClass().getSimpleName(), e);
        }
    }

    int purge(LocalDateTime cutoff) {
        return maskWriterIp("post", "post_id", cutoff)
                + maskWriterIp("comment", "comment_id", cutoff)
                + clearReactionIp(cutoff);
    }

    /** 배치마다 따로 커밋해 긴 락을 피한다. updated_at을 그대로 둬 글이 수정된 것처럼 보이지 않게 한다. */
    private int maskWriterIp(String table, String idColumn, LocalDateTime cutoff) {
        String select =
                "SELECT "
                        + idColumn
                        + " AS id, writer_ip FROM "
                        + table
                        + " WHERE "
                        + idColumn
                        + " > ? AND created_at < ? AND writer_ip NOT LIKE '%.0.0' ORDER BY "
                        + idColumn
                        + " LIMIT ?";
        String update =
                "UPDATE "
                        + table
                        + " SET writer_ip = ?, updated_at = updated_at WHERE "
                        + idColumn
                        + " = ?";
        int total = 0;
        long lastId = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Map<String, Object>> rows = jdbc.queryForList(select, lastId, cutoff, BATCH_SIZE);
            if (rows.isEmpty()) {
                break;
            }
            List<Object[]> masked =
                    rows.stream()
                            .map(
                                    row ->
                                            new Object[] {
                                                twoOctetsOnly((String) row.get("writer_ip")),
                                                row.get("id")
                                            })
                            .toList();
            jdbc.batchUpdate(update, masked);
            total += rows.size();
            lastId = ((Number) rows.get(rows.size() - 1).get("id")).longValue();
        }
        return total;
    }

    private int clearReactionIp(LocalDateTime cutoff) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            int cleared =
                    jdbc.update(
                            "UPDATE post_reaction SET writer_ip = NULL, updated_at = updated_at"
                                    + " WHERE writer_ip IS NOT NULL AND created_at < ?"
                                    + " ORDER BY reaction_id LIMIT ?",
                            cutoff,
                            BATCH_SIZE);
            total += cleared;
            if (cleared < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    static String twoOctetsOnly(String ip) {
        return WriterDisplayFormatter.maskIp(ip).replace("***", "0");
    }
}
