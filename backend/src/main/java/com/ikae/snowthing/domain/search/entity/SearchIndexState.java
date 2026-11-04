package com.ikae.snowthing.domain.search.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 원문별 마지막 적용 버전과 tombstone (028). 내용은 담지 않는다. 읽기·쓰기는 JDBC로 하고 이 엔티티는 스키마 정의다. */
@Entity
@Table(name = "search_index_state")
@IdClass(SearchIndexState.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchIndexState {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SearchSourceType sourceType;

    @Id
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "applied_version", nullable = false)
    private long appliedVersion;

    @Column(name = "tombstoned", nullable = false)
    private boolean tombstoned;

    @Column(name = "document_count", nullable = false)
    private int documentCount;

    @Column(name = "tombstoned_at")
    private LocalDateTime tombstonedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private SearchSourceType sourceType;
        private Long sourceId;
    }
}
