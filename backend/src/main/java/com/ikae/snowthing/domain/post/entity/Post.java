package com.ikae.snowthing.domain.post.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(
        sql =
                "UPDATE post SET is_deleted = true, status = 'DELETED', deleted_at = NOW() WHERE post_id = ?")
@SQLRestriction("is_deleted = false")
public class Post extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private PostCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "writer_ip", nullable = false, length = 45)
    private String writerIp;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Column(name = "anonymous_password")
    private String anonymousPassword;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "comment_count", nullable = false)
    private int commentCount = 0;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    @Column(name = "dislike_count", nullable = false)
    private int dislikeCount = 0;

    @Column(name = "has_image", nullable = false)
    private boolean hasImage = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status = PostStatus.NORMAL;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();

    @Builder
    public Post(
            Member member,
            PostCategory category,
            String title,
            String content,
            String writerIp,
            boolean isAnonymous,
            String anonymousPassword,
            boolean hasImage,
            PostStatus status) {
        this.publicId = UUID.randomUUID().toString();
        this.member = member;
        this.category = category;
        this.title = title;
        this.content = content;
        this.writerIp = writerIp;
        this.isAnonymous = isAnonymous;
        this.anonymousPassword = anonymousPassword;
        this.hasImage = hasImage;
        this.status = status != null ? status : PostStatus.NORMAL;
        this.isDeleted = false;
    }

    public void changeStatus(PostStatus status) {
        this.status = status;
    }

    public void update(String title, String content, PostCategory category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    public void updateImageStatus(boolean hasImage) {
        this.hasImage = hasImage;
    }

    public void replaceImages(List<PostImage> newImages) {
        this.images.clear();
        for (PostImage image : newImages) {
            addImage(image);
        }
        updateImageStatus(!this.images.isEmpty());
    }

    public void softDelete() {
        this.isDeleted = true;
        this.status = PostStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public void increaseCommentCount() {
        this.commentCount++;
    }

    public void decreaseCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void increaseDislikeCount() {
        this.dislikeCount++;
    }

    public void decreaseDislikeCount() {
        if (this.dislikeCount > 0) {
            this.dislikeCount--;
        }
    }

    public void addImage(PostImage image) {
        this.images.add(image);
        image.setPost(this);
    }
}
