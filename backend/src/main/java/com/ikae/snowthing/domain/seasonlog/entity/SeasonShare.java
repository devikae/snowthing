package com.ikae.snowthing.domain.seasonlog.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원이 시즌 결산 공유를 켠 기록. 행이 있을 때만 공개한다. 끄면 행을 지워 예전 링크가 죽는다. */
@Entity
@Table(
        name = "season_share",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_season_share_member_season",
                    columnNames = {"member_id", "season_start_year"}),
            @UniqueConstraint(name = "uk_season_share_token", columnNames = "share_token")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_share_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "season_start_year", nullable = false)
    private int seasonStartYear;

    @Column(name = "share_token", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String shareToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SeasonShare(Member member, int seasonStartYear, LocalDateTime createdAt) {
        this.member = member;
        this.seasonStartYear = seasonStartYear;
        this.shareToken = UUID.randomUUID().toString();
        this.createdAt = createdAt;
    }
}
