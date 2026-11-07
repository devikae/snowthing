package com.ikae.snowthing.global.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

@SpringBootTest
class WriterIpRetentionSchedulerTest {

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 8, 7, 4, 50);
    private static final LocalDateTime OLD = CUTOFF.minusDays(1);
    private static final LocalDateTime RECENT = CUTOFF.plusDays(1);
    private static final LocalDateTime EDITED = LocalDateTime.of(2026, 8, 1, 1, 1);

    @Autowired private WriterIpRetentionScheduler scheduler;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PostCategoryRepository categories;

    private Long categoryId;
    private Long oldPost;
    private Long recentPost;

    @BeforeEach
    void setUp() {
        categoryId =
                categories
                        .findByCode("ANONYMOUS")
                        .orElseGet(
                                () ->
                                        categories.save(
                                                PostCategory.builder()
                                                        .code("ANONYMOUS")
                                                        .name("익명")
                                                        .build()))
                        .getId();
        oldPost = insertPost("58.120.33.44", OLD);
        recentPost = insertPost("58.120.55.66", RECENT);
    }

    @AfterEach
    void tearDown() {
        for (Long postId : new Long[] {oldPost, recentPost}) {
            jdbc.update("DELETE FROM post_reaction WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM comment WHERE post_id = ?", postId);
            jdbc.update("DELETE FROM post WHERE post_id = ?", postId);
        }
    }

    @Test
    @DisplayName("3개월 지난 글과 댓글은 앞 두 자리만 남고 익명 표시와 수정 시각은 그대로다")
    void masksOldWriterIpOnly() {
        Long oldComment = insertComment(oldPost, "211.36.1.2", OLD);
        Long recentComment = insertComment(oldPost, "211.36.3.4", RECENT);
        String displayBefore = WriterDisplayFormatter.maskIp(ipOfPost(oldPost));

        scheduler.purge(CUTOFF);

        assertThat(ipOfPost(oldPost)).isEqualTo("58.120.0.0");
        assertThat(WriterDisplayFormatter.maskIp(ipOfPost(oldPost))).isEqualTo(displayBefore);
        assertThat(ipOfPost(recentPost)).isEqualTo("58.120.55.66");
        assertThat(ipOfComment(oldComment)).isEqualTo("211.36.0.0");
        assertThat(ipOfComment(recentComment)).isEqualTo("211.36.3.4");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT updated_at FROM post WHERE post_id = ?",
                                LocalDateTime.class,
                                oldPost))
                .isEqualTo(EDITED);
    }

    @Test
    @DisplayName("IPv6도 지난 뒤의 익명 표시가 바뀌지 않는다")
    void keepsDisplayForIpv6() {
        String ipv6 = "2001:db8::1";
        assertThat(WriterDisplayFormatter.maskIp(WriterIpRetentionScheduler.twoOctetsOnly(ipv6)))
                .isEqualTo(WriterDisplayFormatter.maskIp(ipv6));
    }

    @Test
    @DisplayName("3개월 지난 추천 IP만 지우고 중복 방지 쿠키 값은 남긴다")
    void clearsOldReactionIpOnly() {
        String voter = UUID.randomUUID().toString();
        insertReaction(oldPost, "58.120.7.7", voter, OLD);
        insertReaction(recentPost, "58.120.8.8", voter, RECENT);

        scheduler.purge(CUTOFF);

        assertThat(ipOfReaction(oldPost)).isNull();
        assertThat(ipOfReaction(recentPost)).isEqualTo("58.120.8.8");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT anonymous_voter_id FROM post_reaction WHERE post_id = ?",
                                String.class,
                                oldPost))
                .isEqualTo(voter);
    }

    private Long insertPost(String ip, LocalDateTime createdAt) {
        String publicId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO post (public_id, category_id, title, content, writer_ip,"
                        + " is_anonymous, view_count, comment_count, like_count, dislike_count,"
                        + " has_image, status, is_deleted, content_version, created_at, updated_at)"
                        + " VALUES (?, ?, '제목', '본문', ?, true, 0, 0, 0, 0, false, 'NORMAL', false, 0,"
                        + " ?, ?)",
                publicId,
                categoryId,
                ip,
                createdAt,
                EDITED);
        return jdbc.queryForObject(
                "SELECT post_id FROM post WHERE public_id = ?", Long.class, publicId);
    }

    private Long insertComment(Long postId, String ip, LocalDateTime createdAt) {
        String content = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO comment (post_id, content, writer_ip, is_anonymous, is_deleted,"
                        + " moderation_held, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, true, false, false, 0, ?, ?)",
                postId,
                content,
                ip,
                createdAt,
                EDITED);
        return jdbc.queryForObject(
                "SELECT comment_id FROM comment WHERE content = ?", Long.class, content);
    }

    private void insertReaction(Long postId, String ip, String voter, LocalDateTime createdAt) {
        jdbc.update(
                "INSERT INTO post_reaction (post_id, writer_ip, anonymous_voter_id, type,"
                        + " created_at, updated_at) VALUES (?, ?, ?, 'LIKE', ?, ?)",
                postId,
                ip,
                voter,
                createdAt,
                EDITED);
    }

    private String ipOfPost(Long id) {
        return jdbc.queryForObject(
                "SELECT writer_ip FROM post WHERE post_id = ?", String.class, id);
    }

    private String ipOfComment(Long id) {
        return jdbc.queryForObject(
                "SELECT writer_ip FROM comment WHERE comment_id = ?", String.class, id);
    }

    private String ipOfReaction(Long postId) {
        return jdbc.queryForObject(
                "SELECT writer_ip FROM post_reaction WHERE post_id = ?", String.class, postId);
    }
}
