package com.ikae.snowthing.domain.comment.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.hibernate.annotations.SQLDelete;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "comment",
        indexes = {
            @Index(
                    name = "idx_comment_post_parent_id",
                    columnList = "post_id,parent_id,comment_id"),
            @Index(
                    name = "idx_comment_parent_deleted_id",
                    columnList = "parent_id,is_deleted,comment_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE comment SET is_deleted = true, deleted_at = NOW() WHERE comment_id = ?")
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "writer_ip", nullable = false, length = 45)
    private String writerIp;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Column(name = "anonymous_password")
    private String anonymousPassword;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Comment(
            Post post,
            Member member,
            Comment parent,
            String content,
            String writerIp,
            boolean isAnonymous,
            String anonymousPassword) {
        this.post = post;
        this.member = member;
        this.parent = parent;
        this.content = content;
        this.writerIp = writerIp;
        this.isAnonymous = isAnonymous;
        this.anonymousPassword = anonymousPassword;
        this.isDeleted = false;
    }

    public static Comment create(
            Post post,
            Member member,
            Comment parent,
            String content,
            String writerIp,
            boolean isAnonymous,
            String anonymousPassword) {
        return new Comment(post, member, parent, content, writerIp, isAnonymous, anonymousPassword);
    }

    public Comment rootParent() {
        Comment current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
