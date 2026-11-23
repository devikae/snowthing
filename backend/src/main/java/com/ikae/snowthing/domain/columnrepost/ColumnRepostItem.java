package com.ikae.snowthing.domain.columnrepost;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 휘구리 칼럼 예약 게시 대기열 한 줄. 줄은 수집 스크립트가 만든 SQL로 넣고, 애플리케이션은 게시 상태만 바꾼다. */
@Entity
@Table(
        name = "column_repost_queue",
        indexes =
                @Index(
                        name = "idx_column_repost_status_sequence",
                        columnList = "status, sequence_no"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ColumnRepostItem {

    static final String PENDING = "PENDING";
    static final String PUBLISHED = "PUBLISHED";
    private static final int ERROR_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sequence_no", nullable = false, unique = true)
    private Integer sequenceNo;

    @Column(name = "source_no", nullable = false, unique = true)
    private Long sourceNo;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "post_public_id", length = 36)
    private String postPublicId;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", length = ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    ColumnRepostItem(
            int sequenceNo, long sourceNo, String title, String content, LocalDateTime now) {
        this.sequenceNo = sequenceNo;
        this.sourceNo = sourceNo;
        this.title = title;
        this.content = content;
        this.status = PENDING;
        this.createdAt = now;
    }

    boolean isPending() {
        return PENDING.equals(status);
    }

    void markPublished(String postPublicId, LocalDateTime now) {
        this.status = PUBLISHED;
        this.postPublicId = postPublicId;
        this.publishedAt = now;
        this.lastError = null;
    }

    void recordFailure(String error) {
        this.attempts++;
        this.lastError =
                error == null || error.length() <= ERROR_LENGTH
                        ? error
                        : error.substring(0, ERROR_LENGTH);
    }
}
