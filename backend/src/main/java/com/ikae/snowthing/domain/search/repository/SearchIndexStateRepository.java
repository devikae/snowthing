package com.ikae.snowthing.domain.search.repository;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;

import lombok.RequiredArgsConstructor;

/**
 * search_index_state 읽기·쓰기. 원문별 마지막 적용 버전과 tombstone만 다루고 내용은 저장하지 않는다.
 *
 * <p>상태 행은 같은 원문을 동시에 처리하지 못하게 막는 잠금 역할도 한다. 처리기는 원문을 읽기 전에 {@link #lock}으로 이 행을 잡는다.
 */
@Repository
@RequiredArgsConstructor
public class SearchIndexStateRepository {

    /** 아직 색인에 반영한 적이 없는 원문의 적용 버전. */
    public static final long NOT_APPLIED = -1;

    private final JdbcTemplate jdbc;

    /**
     * 상태 행을 배타 잠금으로 잡고 현재 적용 버전을 돌려준다. 행이 없으면 "반영 전·문서 없음"(-1, tombstoned)으로 만든다. INSERT ... ON
     * DUPLICATE KEY UPDATE는 기존 행에 바로 배타 잠금을 건다. INSERT IGNORE 뒤 SELECT ... FOR UPDATE는 공유 잠금에서 배타
     * 잠금으로 올리는 과정에서 두 트랜잭션이 서로 기다리는 교착이 생길 수 있어 쓰지 않는다.
     */
    public long lock(SearchSourceType type, long sourceId, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO search_index_state (source_type, source_id, applied_version,"
                        + " tombstoned, document_count, tombstoned_at, updated_at)"
                        + " VALUES (?, ?, ?, true, 0, NULL, ?)"
                        + " ON DUPLICATE KEY UPDATE source_id = source_id",
                type.name(),
                sourceId,
                NOT_APPLIED,
                now);
        Long applied =
                jdbc.queryForObject(
                        "SELECT applied_version FROM search_index_state"
                                + " WHERE source_type = ? AND source_id = ? FOR UPDATE",
                        Long.class,
                        type.name(),
                        sourceId);
        return applied == null ? NOT_APPLIED : applied;
    }

    /** 문서를 반영한 상태. documentCount는 이 원문 자신의 문서 수다(게시글이면 댓글 문서는 세지 않는다). */
    public void markApplied(
            SearchSourceType type,
            long sourceId,
            long version,
            int documentCount,
            LocalDateTime now) {
        jdbc.update(
                "UPDATE search_index_state SET applied_version = ?, tombstoned = false,"
                        + " document_count = ?, tombstoned_at = NULL, updated_at = ?"
                        + " WHERE source_type = ? AND source_id = ?",
                version,
                documentCount,
                now,
                type.name(),
                sourceId);
    }

    /** 문서를 지운 상태로 남긴다. 처음 tombstone이 될 때의 시각을 유지한다. 반영된 적이 없던 행은 tombstoned_at이 NULL로 남는다. */
    public void markTombstoned(
            SearchSourceType type, long sourceId, long version, LocalDateTime now) {
        jdbc.update(
                "UPDATE search_index_state SET"
                        + " tombstoned_at = IF(tombstoned, tombstoned_at, ?),"
                        + " applied_version = ?, tombstoned = true, document_count = 0,"
                        + " updated_at = ?"
                        + " WHERE source_type = ? AND source_id = ?",
                now,
                version,
                now,
                type.name(),
                sourceId);
    }
}
