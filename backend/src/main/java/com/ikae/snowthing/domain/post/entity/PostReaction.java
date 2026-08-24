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
        @UniqueConstraint(name = "uk_post_member_type", columnNames = {"post_id", "member_id", "type"}),
        @UniqueConstraint(name = "uk_post_anon_voter_type", columnNames = {"post_id", "anonymous_voter_id", "type"})
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
    @JoinColumn(name = "member_id", nullable = true)
    private Member member;

    @Column(name = "writer_ip", length = 45)
    private String writerIp;

    @Column(name = "anonymous_voter_id", length = 36)
    private String anonymousVoterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReactionType type;

    @Builder
    public PostReaction(Post post, Member member, String writerIp, String anonymousVoterId, ReactionType type) {
        this.post = post;
        this.member = member;
        this.writerIp = writerIp;
        this.anonymousVoterId = anonymousVoterId;
        this.type = type;
    }
}
