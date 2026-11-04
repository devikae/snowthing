package com.ikae.snowthing.domain.post.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.Check;
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
                "UPDATE post SET is_deleted = true, status = 'DELETED', deleted_at = NOW(),"
                        + " content_version = content_version + 1 WHERE post_id = ?")
@SQLRestriction("is_deleted = false")
@Check(constraints = "like_count >= 0 AND dislike_count >= 0")
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

    /**
     * 검색 근거용 내용 버전. 제목·본문·카테고리·상태·삭제가 바뀔 때만 1 올린다. 낙관적 락(@Version)이 아니다. 조회수·반응 수 갱신과 비관적 락 경로에는
     * 영향을 주지 않는다.
     */
    @Column(name = "content_version", nullable = false)
    private long contentVersion = 0L;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostImage> images = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "post_content_image_reference",
            joinColumns = @JoinColumn(name = "post_id"),
            uniqueConstraints =
                    @UniqueConstraint(
                            name = "uk_post_content_image",
                            columnNames = {"post_id", "image_key"}),
            indexes = @Index(name = "idx_content_image_key", columnList = "image_key"))
    @Column(name = "image_key", nullable = false, length = 160)
    private Set<String> contentImageKeys = new HashSet<>();

    public void replaceContentImageKeys(Set<String> keys) {
        var copy = Set.copyOf(keys);
        contentImageKeys.clear();
        contentImageKeys.addAll(copy);
    }

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
        if (this.status != status) {
            this.contentVersion++;
        }
        this.status = status;
    }

    public void update(String title, String content, PostCategory category) {
        if (!Objects.equals(this.title, title)
                || !Objects.equals(this.content, content)
                || !isSameCategory(this.category, category)) {
            this.contentVersion++;
        }
        this.title = title;
        this.content = content;
        this.category = category;
    }

    private static boolean isSameCategory(PostCategory current, PostCategory next) {
        if (current == next) {
            return true;
        }
        if (current == null || next == null || current.getId() == null) {
            return false;
        }
        return current.getId().equals(next.getId());
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
        if (!this.isDeleted) {
            this.contentVersion++;
        }
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
