package com.ikae.snowthing.domain.comment.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 명시적으로 호출할 때만 benchmark 스키마에 데이터를 생성하는 실행 진입점. */
@Tag("benchmark")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=update")
@ActiveProfiles({"test", "benchmark"})
class CommentBenchmarkSeedRunnerTest {

    private static final String BENCHMARK_POST_PATTERN = "benchmark-sprint04-post-%";
    private static final int PAGE_SIZE = 20;

    @Autowired private CommentBenchmarkSeedHarness harness;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void seedRequestedScale() {
        int total = Integer.parseInt(System.getenv().getOrDefault("BENCHMARK_COMMENTS", "1000"));
        long seed = Long.parseLong(System.getenv().getOrDefault("BENCHMARK_SEED", "20260907"));
        CommentBenchmarkSeedHarness.SeedResult result = harness.seed(total, seed);

        assertTotalDistribution(total);
        assertPostDistribution(total);
        assertActiveCommentCountForEveryPost();
        assertReplyLimitAndUniqueIds();
        assertNoRootPageOmissions(result.postIds());
        assertNoReplyPageOmissions();
    }

    private void assertTotalDistribution(int total) {
        Long actual =
                queryCount(
                        "SELECT COUNT(*) FROM comment c JOIN post p ON p.post_id=c.post_id WHERE p.public_id LIKE ?",
                        BENCHMARK_POST_PATTERN);
        Long roots =
                queryCount(
                        "SELECT COUNT(*) FROM comment c JOIN post p ON p.post_id=c.post_id WHERE p.public_id LIKE ? AND c.parent_id IS NULL",
                        BENCHMARK_POST_PATTERN);
        Long replies =
                queryCount(
                        "SELECT COUNT(*) FROM comment c JOIN post p ON p.post_id=c.post_id WHERE p.public_id LIKE ? AND c.parent_id IS NOT NULL",
                        BENCHMARK_POST_PATTERN);
        assertThat(actual).isEqualTo((long) total);
        assertThat(roots + replies).isEqualTo((long) total);
    }

    private void assertPostDistribution(int total) {
        assertThat(
                        queryCount(
                                "SELECT COUNT(*) FROM post WHERE public_id LIKE ?",
                                BENCHMARK_POST_PATTERN))
                .isEqualTo(100L);
        Long hotPostComments =
                queryCount(
                        "SELECT COUNT(*) FROM comment c JOIN post p ON p.post_id=c.post_id WHERE p.public_id='benchmark-sprint04-post-99'");
        assertThat(hotPostComments).isGreaterThanOrEqualTo(Math.max(1L, Math.round(total * 0.09)));
    }

    private void assertActiveCommentCountForEveryPost() {
        List<PostCountInvariant> counts =
                jdbcTemplate.query(
                        "SELECT p.post_id,p.comment_count,COUNT(CASE WHEN c.is_deleted=FALSE THEN 1 END) active_count "
                                + "FROM post p LEFT JOIN comment c ON c.post_id=p.post_id WHERE p.public_id LIKE ? "
                                + "GROUP BY p.post_id,p.comment_count ORDER BY p.post_id",
                        (rs, rowNum) ->
                                new PostCountInvariant(
                                        rs.getLong("post_id"),
                                        rs.getLong("comment_count"),
                                        rs.getLong("active_count")),
                        BENCHMARK_POST_PATTERN);
        assertThat(counts).hasSize(100);
        assertThat(counts)
                .allSatisfy(
                        count ->
                                assertThat(count.storedCount())
                                        .as("post_id=%s 활성 댓글 수", count.postId())
                                        .isEqualTo(count.actualActiveCount()));
    }

    private void assertReplyLimitAndUniqueIds() {
        Long overReplyLimit =
                queryCount(
                        "SELECT COUNT(*) FROM (SELECT c.parent_id FROM comment c JOIN post p ON p.post_id=c.post_id "
                                + "WHERE p.public_id LIKE ? AND c.parent_id IS NOT NULL AND c.is_deleted=FALSE "
                                + "GROUP BY c.parent_id HAVING COUNT(*)>100) over_limit",
                        BENCHMARK_POST_PATTERN);
        Long duplicateIds =
                queryCount(
                        "SELECT COUNT(*)-COUNT(DISTINCT c.comment_id) FROM comment c JOIN post p ON p.post_id=c.post_id WHERE p.public_id LIKE ?",
                        BENCHMARK_POST_PATTERN);
        assertThat(overReplyLimit).isZero();
        assertThat(duplicateIds).isZero();
    }

    private void assertNoRootPageOmissions(List<Long> postIds) {
        for (Long postId : postIds) {
            List<CursorRow> expected =
                    queryRows(
                            "c.post_id=? AND c.parent_id IS NULL", postId, null, Integer.MAX_VALUE);
            List<CursorRow> traversed =
                    traversePages("c.post_id=? AND c.parent_id IS NULL", postId);
            assertCompleteStableTraversal("post_id=" + postId + " 루트", expected, traversed);
        }
    }

    private void assertNoReplyPageOmissions() {
        List<Long> pageableRoots =
                jdbcTemplate.queryForList(
                        "SELECT c.parent_id FROM comment c JOIN post p ON p.post_id=c.post_id "
                                + "WHERE p.public_id LIKE ? AND c.parent_id IS NOT NULL "
                                + "GROUP BY c.parent_id HAVING COUNT(*)>?",
                        Long.class,
                        BENCHMARK_POST_PATTERN,
                        PAGE_SIZE);
        for (Long rootId : pageableRoots) {
            List<CursorRow> expected = queryRows("c.parent_id=?", rootId, null, Integer.MAX_VALUE);
            List<CursorRow> traversed = traversePages("c.parent_id=?", rootId);
            assertCompleteStableTraversal("root_id=" + rootId + " 대댓글", expected, traversed);
        }
    }

    private List<CursorRow> traversePages(String scopeCondition, Long scopeId) {
        List<CursorRow> traversed = new ArrayList<>();
        Long cursorId = null;
        while (true) {
            List<CursorRow> page = queryRows(scopeCondition, scopeId, cursorId, PAGE_SIZE);
            traversed.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return List.copyOf(traversed);
            }
            cursorId = page.get(page.size() - 1).commentId();
        }
    }

    private List<CursorRow> queryRows(
            String scopeCondition, Long scopeId, Long cursorId, int limit) {
        String cursorCondition = cursorId == null ? "" : " AND c.comment_id>?";
        List<Object> args = new ArrayList<>(List.of(scopeId));
        if (cursorId != null) args.add(cursorId);
        args.add(limit);
        return jdbcTemplate.query(
                "SELECT c.comment_id,c.created_at FROM comment c WHERE "
                        + scopeCondition
                        + cursorCondition
                        + " ORDER BY c.comment_id ASC LIMIT ?",
                (rs, rowNum) ->
                        new CursorRow(rs.getLong("comment_id"), rs.getTimestamp("created_at")),
                args.toArray());
    }

    private void assertCompleteStableTraversal(
            String scope, List<CursorRow> expected, List<CursorRow> traversed) {
        assertThat(traversed).as(scope + " 전체 페이지 결과").containsExactlyElementsOf(expected);
        Set<Long> uniqueIds = new HashSet<>();
        for (int index = 0; index < traversed.size(); index++) {
            CursorRow current = traversed.get(index);
            assertThat(uniqueIds.add(current.commentId())).as(scope + " 중복 ID").isTrue();
            if (index == 0) continue;
            CursorRow previous = traversed.get(index - 1);
            assertThat(current.commentId())
                    .as(scope + " ID 정렬")
                    .isGreaterThan(previous.commentId());
            if (current.createdAt().equals(previous.createdAt())) {
                assertThat(current.commentId())
                        .as(scope + " 동일 생성 시각의 ID 타이브레이커")
                        .isGreaterThan(previous.commentId());
            }
        }
    }

    private Long queryCount(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private record CursorRow(long commentId, Timestamp createdAt) {}

    private record PostCountInvariant(long postId, long storedCount, long actualActiveCount) {}
}
