package com.ikae.snowthing.domain.carpool.entity;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카풀 탑승 확인 (043). 출발 뒤 탑승자가 요청하고 운전자가 확정하거나 거절한다. */
@Entity
@Table(
        name = "carpool_rider",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_carpool_rider_post_member",
                        columnNames = {"post_id", "member_id"}),
        indexes =
                @Index(name = "idx_carpool_rider_member_status", columnList = "member_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarpoolRider extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "carpool_rider_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CarpoolDetail carpool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CarpoolRiderStatus status;

    public CarpoolRider(CarpoolDetail carpool, Member member) {
        this.carpool = carpool;
        this.member = member;
        this.status = CarpoolRiderStatus.REQUESTED;
    }

    public boolean isRequested() {
        return status == CarpoolRiderStatus.REQUESTED;
    }

    public void confirm() {
        this.status = CarpoolRiderStatus.CONFIRMED;
    }

    public void reject() {
        this.status = CarpoolRiderStatus.REJECTED;
    }
}
