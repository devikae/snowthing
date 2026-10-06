package com.ikae.snowthing.domain.resortreport.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "resort_report",
        indexes = {
            @Index(
                    name = "idx_resort_report_created_resort",
                    columnList = "created_at DESC, resort_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResortReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ResortReport(Resort resort, Member author, String content, LocalDateTime createdAt) {
        if (author == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (resort == null || !resort.isActive()) {
            throw new CustomException(ErrorCode.RESORT_NOT_FOUND);
        }
        this.resort = resort;
        this.author = author;
        this.content = validateAndNormalizeContent(content);
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    private static String validateAndNormalizeContent(String rawContent) {
        if (rawContent == null) {
            throw new CustomException(ErrorCode.INVALID_RESORT_REPORT_CONTENT);
        }
        String trimmed = rawContent.trim();
        if (trimmed.isEmpty() || trimmed.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_RESORT_REPORT_CONTENT);
        }
        return trimmed;
    }
}
