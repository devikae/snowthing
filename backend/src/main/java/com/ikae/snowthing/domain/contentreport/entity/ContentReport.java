package com.ikae.snowthing.domain.contentreport.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "content_report",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_content_report_target_reporter",
                        columnNames = {"target_type", "target_id", "reporter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_report_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public ContentReport(
            ReportTargetType targetType,
            Long targetId,
            Member reporter,
            ReportReason reason,
            String detail,
            LocalDateTime createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.reporter = reporter;
        this.reason = reason;
        this.detail = detail;
        this.status = ReportStatus.PENDING;
        this.createdAt = createdAt;
    }

    public void resolve(LocalDateTime now) {
        this.status = ReportStatus.RESOLVED;
        this.resolvedAt = now;
    }
}
