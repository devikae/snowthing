package com.ikae.snowthing.domain.slopestatus.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리조트별 슬로프 현황 최신 스냅샷 (046). 리조트당 한 줄이다.
 *
 * <p>수집에 성공하면 슬로프 목록과 수집 시각을 통째로 바꾼다. 실패하면 마지막 성공 값은 그대로 두고 시도 시각과 실패 상태만 바꾼다. 그래서 화면은 언제나 마지막 성공
 * 값과 그 시각을 보여 줄 수 있다.
 */
@Entity
@Table(
        name = "resort_slope_status",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_resort_slope_status_resort",
                        columnNames = {"resort_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResortSlopeStatus extends BaseTimeEntity {

    private static final int FAILURE_REASON_MAX = 200;
    private static final int OPERATING_NOTE_MAX = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resort_slope_status_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    /** 사용자에게 보여 줄 공식 페이지 주소. */
    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    /** 마지막 성공 슬로프 목록 JSON. 한 번도 성공하지 못했으면 null. */
    @Column(name = "slopes_json", columnDefinition = "MEDIUMTEXT")
    private String slopesJson;

    @Column(name = "open_slope_count")
    private Integer openSlopeCount;

    @Column(name = "total_slope_count")
    private Integer totalSlopeCount;

    @Column(name = "operating_note", length = OPERATING_NOTE_MAX)
    private String operatingNote;

    /** 마지막 성공 시각(KST). */
    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    /** 마지막 시도 시각(KST). 성공이든 실패든 바뀐다. */
    @Column(name = "last_attempt_at", nullable = false)
    private LocalDateTime lastAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_result", nullable = false, length = 20)
    private CollectResult lastResult;

    /** 운영 확인용. API로 내보내지 않는다. */
    @Column(name = "failure_reason", length = FAILURE_REASON_MAX)
    private String failureReason;

    private ResortSlopeStatus(Resort resort, String sourceUrl) {
        this.resort = resort;
        this.sourceUrl = sourceUrl;
    }

    public static ResortSlopeStatus succeeded(
            Resort resort,
            String sourceUrl,
            String slopesJson,
            int openCount,
            int totalCount,
            String operatingNote,
            LocalDateTime now) {
        ResortSlopeStatus status = new ResortSlopeStatus(resort, sourceUrl);
        status.recordSuccess(sourceUrl, slopesJson, openCount, totalCount, operatingNote, now);
        return status;
    }

    public static ResortSlopeStatus failed(
            Resort resort, String sourceUrl, String reason, LocalDateTime now) {
        ResortSlopeStatus status = new ResortSlopeStatus(resort, sourceUrl);
        status.recordFailure(reason, now);
        return status;
    }

    public void recordSuccess(
            String sourceUrl,
            String slopesJson,
            int openCount,
            int totalCount,
            String operatingNote,
            LocalDateTime now) {
        this.sourceUrl = sourceUrl;
        this.slopesJson = slopesJson;
        this.openSlopeCount = openCount;
        this.totalSlopeCount = totalCount;
        this.operatingNote = truncate(operatingNote, OPERATING_NOTE_MAX);
        this.collectedAt = now;
        this.lastAttemptAt = now;
        this.lastResult = CollectResult.SUCCESS;
        this.failureReason = null;
    }

    public void recordFailure(String reason, LocalDateTime now) {
        this.lastAttemptAt = now;
        this.lastResult = CollectResult.FAILED;
        this.failureReason = truncate(reason, FAILURE_REASON_MAX);
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
