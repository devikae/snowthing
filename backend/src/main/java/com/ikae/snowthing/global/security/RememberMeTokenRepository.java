package com.ikae.snowthing.global.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 유지 토큰(persistent_logins) 읽기와 쓰기. 운영 정의는
 * database/production/056_migration_persistent_logins.sql.
 *
 * <p>쿠키에 담긴 토큰 원문은 저장하지 않고 SHA-256 해시만 둔다. 표가 새어도 쿠키를 만들어 낼 수 없게 한다. 회원은 이메일이 아니라 공개 ID로 묶는다. 탈퇴하면
 * 이메일이 익명값으로 바뀌어 이메일로는 토큰을 지울 수 없기 때문이다.
 */
@Repository
@RequiredArgsConstructor
public class RememberMeTokenRepository {

    private final JdbcTemplate jdbc;

    public record StoredToken(
            String series,
            String memberPublicId,
            String tokenHash,
            String previousTokenHash,
            LocalDateTime lastUsedAt) {}

    public void create(String series, String memberPublicId, String tokenHash, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO persistent_logins (series, member_public_id, token_hash,"
                        + " previous_token_hash, last_used_at, created_at)"
                        + " VALUES (?, ?, ?, NULL, ?, ?)",
                series,
                memberPublicId,
                tokenHash,
                now,
                now);
    }

    public Optional<StoredToken> findBySeries(String series) {
        List<StoredToken> rows =
                jdbc.query(
                        "SELECT series, member_public_id, token_hash, previous_token_hash,"
                                + " last_used_at FROM persistent_logins WHERE series = ?",
                        (rs, rowNum) ->
                                new StoredToken(
                                        rs.getString("series"),
                                        rs.getString("member_public_id"),
                                        rs.getString("token_hash"),
                                        rs.getString("previous_token_hash"),
                                        rs.getObject("last_used_at", LocalDateTime.class)),
                        series);
        return rows.stream().findFirst();
    }

    /**
     * 읽을 때의 토큰 해시가 그대로일 때만 새 토큰으로 바꾼다. 같은 쿠키로 요청 두 개가 동시에 들어와도 한 요청만 바꾸게 해서, 브라우저가 받은 쿠키와 표의 토큰이
     * 어긋나지 않게 한다.
     *
     * @return 이 요청이 바꿨으면 true, 다른 요청이 먼저 바꿨으면 false
     */
    public boolean rotate(
            String series, String expectedTokenHash, String newTokenHash, LocalDateTime now) {
        int updated =
                jdbc.update(
                        "UPDATE persistent_logins SET previous_token_hash = token_hash,"
                                + " token_hash = ?, last_used_at = ?"
                                + " WHERE series = ? AND token_hash = ?",
                        newTokenHash,
                        now,
                        series,
                        expectedTokenHash);
        return updated == 1;
    }

    public void deleteSeries(String series) {
        jdbc.update("DELETE FROM persistent_logins WHERE series = ?", series);
    }

    public void deleteByMember(String memberPublicId) {
        jdbc.update("DELETE FROM persistent_logins WHERE member_public_id = ?", memberPublicId);
    }

    public int deleteLastUsedBefore(LocalDateTime threshold) {
        return jdbc.update("DELETE FROM persistent_logins WHERE last_used_at < ?", threshold);
    }
}
