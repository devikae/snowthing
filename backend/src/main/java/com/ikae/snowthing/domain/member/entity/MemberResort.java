package com.ikae.snowthing.domain.member.entity;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member_resort",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"member_id", "resort_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberResort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    @Builder
    public MemberResort(Member member, Resort resort) {
        this.member = member;
        this.resort = resort;
    }
}
