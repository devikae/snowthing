package com.ikae.snowthing.domain.comment.spike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

import lombok.RequiredArgsConstructor;

/** MySQL benchmark schema에만 대량 댓글을 JDBC batch로 주입하는 전용 하네스. */
@Component
@RequiredArgsConstructor
public class CommentBenchmarkSeedHarness {

    private static final String BENCHMARK_PREFIX = "benchmark-sprint04-";
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public record SeedResult(long memberId, List<Long> postIds, long commentCount) {
        public SeedResult {
            postIds = List.copyOf(postIds);
        }
    }

    public SeedResult seed(int totalComments, long seed) {
        if (totalComments < 1_000) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
        assertSafeDatabase();
        cleanup();
        Random random = new Random(seed);

        long memberId = seedMember();
        jdbcTemplate.update(
                "INSERT INTO post_category (name, code) VALUES ('자유게시판', 'FREE') ON DUPLICATE KEY UPDATE category_id = category_id");
        long categoryId =
                jdbcTemplate.queryForObject(
                        "SELECT category_id FROM post_category WHERE code = 'FREE' LIMIT 1",
                        Long.class);
        List<Long> postIds = seedPosts(memberId, categoryId, totalComments, random);
        List<Long> roots = new ArrayList<>();
        int rootCount = Math.max(100, totalComments / 5);
        executeBatchInChunks(
                "INSERT INTO comment (post_id, member_id, parent_id, content, writer_ip, is_anonymous, is_deleted, version, created_at, updated_at) VALUES (?, ?, NULL, ?, ?, ?, ?, 0, ?, NOW())",
                buildRootArgs(postIds, memberId, rootCount, random));
        roots.addAll(
                jdbcTemplate.query(
                        """
                        SELECT c.comment_id
                        FROM comment c
                        JOIN post p ON p.post_id = c.post_id
                        WHERE p.public_id LIKE ?
                          AND c.parent_id IS NULL
                          AND c.content LIKE '%benchmark-sprint04-root-%'
                        ORDER BY c.comment_id
                        """,
                        (rs, rowNum) -> rs.getLong(1), BENCHMARK_PREFIX + "post-%"));

        int replyCount = totalComments - roots.size();
        if (replyCount > 0 && !roots.isEmpty()) {
            executeBatchInChunks(
                    "INSERT INTO comment (post_id, member_id, parent_id, content, writer_ip, is_anonymous, is_deleted, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, NOW())",
                    buildReplyArgs(postIds, roots, memberId, replyCount, random));
        }
        jdbcTemplate.update(
                "UPDATE post p SET comment_count = (SELECT COUNT(*) FROM comment c WHERE c.post_id = p.post_id AND c.is_deleted = FALSE) WHERE p.public_id LIKE ?",
                BENCHMARK_PREFIX + "%");
        return new SeedResult(memberId, postIds, totalComments);
    }

    private void executeBatchInChunks(String sql, List<Object[]> args) {
        final int chunkSize = 5_000;
        for (int from = 0; from < args.size(); from += chunkSize) {
            int to = Math.min(from + chunkSize, args.size());
            jdbcTemplate.batchUpdate(sql, args.subList(from, to));
        }
    }

    private void assertSafeDatabase() {
        try (var connection = dataSource.getConnection()) {
            String database = connection.getCatalog();
            String url = connection.getMetaData().getURL().toLowerCase();
            if (database == null
                    || !(database.contains("test") || database.contains("benchmark"))
                    || url.contains("prod")) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
        } catch (Exception e) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE c FROM comment c JOIN comment parent ON parent.comment_id = c.parent_id JOIN post p ON p.post_id = parent.post_id WHERE p.public_id LIKE ?",
                BENCHMARK_PREFIX + "%");
        jdbcTemplate.update(
                "DELETE c FROM comment c JOIN post p ON p.post_id = c.post_id WHERE p.public_id LIKE ?",
                BENCHMARK_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM post WHERE public_id LIKE ?", BENCHMARK_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM member WHERE public_id = ?", BENCHMARK_PREFIX + "member");
    }

    private long seedMember() {
        jdbcTemplate.update(
                "INSERT INTO member (public_id,email,password,nickname,role,status,created_at,updated_at) VALUES (?,?,?,?,?,?,NOW(),NOW())",
                BENCHMARK_PREFIX + "member",
                BENCHMARK_PREFIX + "member@snowthing.test",
                "benchmark-password-hash",
                "benchmark-member",
                "ROLE_USER",
                "ACTIVE");
        return jdbcTemplate.queryForObject(
                "SELECT member_id FROM member WHERE public_id = ?",
                Long.class,
                BENCHMARK_PREFIX + "member");
    }

    private List<Long> seedPosts(long memberId, long categoryId, int total, Random random) {
        int postCount = 100;
        for (int i = 0; i < postCount; i++) {
            jdbcTemplate.update(
                    "INSERT INTO post (public_id,member_id,category_id,title,content,writer_ip,is_anonymous,view_count,comment_count,like_count,dislike_count,has_image,status,is_deleted,created_at,updated_at) VALUES (?,?,?,?,?,?,FALSE,0,0,0,0,FALSE,'NORMAL',FALSE,NOW(),NOW())",
                    BENCHMARK_PREFIX + "post-" + i,
                    memberId,
                    categoryId,
                    RealisticContentGenerator.postTitle(random, i),
                    RealisticContentGenerator.postBody(random, i),
                    "127.0.0.1");
        }
        return jdbcTemplate.query(
                "SELECT post_id FROM post WHERE public_id LIKE ? ORDER BY post_id",
                (rs, n) -> rs.getLong(1),
                BENCHMARK_PREFIX + "post-%");
    }

    private List<Object[]> buildRootArgs(List<Long> posts, long member, int count, Random random) {
        List<Object[]> args = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            args.add(
                    new Object[] {
                        posts.get(distributedPostIndex(i, count, posts.size())),
                        member,
                        RealisticContentGenerator.rootComment(random, i),
                        "127.0.0.1",
                        i % 10 == 0,
                        i % 5 == 0,
                        java.sql.Timestamp.valueOf("2026-01-01 00:00:00")
                    });
        }
        return args;
    }

    private List<Object[]> buildReplyArgs(
            List<Long> posts, List<Long> roots, long member, int count, Random random) {
        List<Object[]> args = new ArrayList<>();
        int hotspotReplyCount = Math.min(100, count);
        for (int i = 0; i < count; i++) {
            int rootIndex = i < hotspotReplyCount ? 0 : i % roots.size();
            long root = roots.get(rootIndex);
            args.add(
                    new Object[] {
                        posts.get(distributedPostIndex(rootIndex, roots.size(), posts.size())),
                        member,
                        root,
                        RealisticContentGenerator.reply(random, i),
                        "127.0.0.1",
                        i % 10 == 0,
                        i >= hotspotReplyCount && i % 5 == 0,
                        java.sql.Timestamp.valueOf("2026-01-01 00:00:00")
                    });
        }
        return args;
    }

    /** 45% general posts, 45% medium posts, 10% hot post. */
    private int distributedPostIndex(int ordinal, int total, int postCount) {
        if (postCount < 100) return ordinal % postCount;
        int general = Math.max(1, (int) Math.floor(total * 0.45));
        int medium = Math.max(general + 1, (int) Math.floor(total * 0.90));
        if (ordinal < general) return ordinal % 80;
        if (ordinal < medium) return 80 + (ordinal % 19);
        return 99;
    }
}
