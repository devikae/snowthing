package com.ikae.snowthing.domain.post.entity;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "post_reaction",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_post_member_type", columnNames = {"post_id", "member_id", "type"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reaction_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReactionType type;

    @Builder
    public PostReaction(Post post, Member member, ReactionType type) {
        this.post = post;
        this.member = member;
        this.type = type;
    }
}
