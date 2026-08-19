package com.ikae.snowthing.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member_riding_style",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"member_id", "riding_style_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberRidingStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "riding_style_id", nullable = false)
    private RidingStyle ridingStyle;

    @Builder
    public MemberRidingStyle(Member member, RidingStyle ridingStyle) {
        this.member = member;
        this.ridingStyle = ridingStyle;
    }
}
