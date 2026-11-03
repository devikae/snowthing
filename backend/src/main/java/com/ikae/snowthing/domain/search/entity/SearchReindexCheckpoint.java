package com.ikae.snowthing.domain.search.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 무중단 재인덱싱 진행 위치 (028). 읽기·쓰기는 JDBC로 하고 이 엔티티는 스키마 정의다. */
@Entity
@Table(name = "search_reindex_checkpoint")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchReindexCheckpoint {

    @Id
    @Column(name = "job_name", length = 40)
    private String jobName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "last_source_id", nullable = false)
    private long lastSourceId;

    @Column(name = "enqueued_count", nullable = false)
    private long enqueuedCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
