package com.ikae.snowthing.domain.post.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    interface ReactionCounts {
        int getLikeCount();

        int getDislikeCount();
    }

    interface ReactionCountMismatchProjection {
        Long getPostId();

        String getPostPublicId();

        long getStoredCount();

        long getActualCount();
    }

    Optional<Post> findByPublicId(String publicId);

    @Query(
            "SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category WHERE p.publicId = :publicId")
    Optional<Post> findWithMemberAndCategoryByPublicId(@Param("publicId") String publicId);

    @Query(
            "SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category WHERE p.category.code = :categoryCode AND p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL AND p.isDeleted = false")
    Page<Post> findByCategoryCodeWithMemberAndCategory(
            @Param("categoryCode") String categoryCode, Pageable pageable);

    @Query(
            value =
                    "SELECT p FROM Post p LEFT JOIN FETCH p.member JOIN FETCH p.category WHERE p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL AND p.isDeleted = false",
            countQuery =
                    "SELECT COUNT(p) FROM Post p WHERE p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL AND p.isDeleted = false")
    Page<Post> findAllWithMemberAndCategory(Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
    void increaseViewCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    int increaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    int decreaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.dislikeCount = p.dislikeCount + 1 WHERE p.id = :postId")
    int increaseDislikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.dislikeCount = p.dislikeCount - 1 WHERE p.id = :postId AND p.dislikeCount > 0")
    int decreaseDislikeCount(@Param("postId") Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :postId")
    Optional<Post> findByIdForUpdate(@Param("postId") Long postId);

    @Query(
            "SELECT p.likeCount AS likeCount, p.dislikeCount AS dislikeCount FROM Post p WHERE p.id = :postId")
    Optional<ReactionCounts> findReactionCountsById(@Param("postId") Long postId);

    @Query(
            value =
                    "SELECT p.like_count AS likeCount, p.dislike_count AS dislikeCount FROM post p WHERE p.post_id = :postId FOR UPDATE",
            nativeQuery = true)
    Optional<ReactionCounts> findReactionCountsByIdForUpdate(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE post p SET p.like_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type) WHERE p.post_id = :postId",
            nativeQuery = true)
    int reconcileLikeCount(@Param("postId") Long postId, @Param("type") String type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "UPDATE post p SET p.dislike_count = (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type) WHERE p.post_id = :postId",
            nativeQuery = true)
    int reconcileDislikeCount(@Param("postId") Long postId, @Param("type") String type);

    @Query(
            value =
                    "SELECT p.post_id AS postId, p.public_id AS postPublicId, p.like_count AS storedCount, (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type) AS actualCount FROM post p WHERE p.like_count <> (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type)",
            nativeQuery = true)
    List<ReactionCountMismatchProjection> findLikeCountMismatches(@Param("type") String type);

    @Query(
            value =
                    "SELECT p.post_id AS postId, p.public_id AS postPublicId, p.dislike_count AS storedCount, (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type) AS actualCount FROM post p WHERE p.dislike_count <> (SELECT COUNT(*) FROM post_reaction r WHERE r.post_id = p.post_id AND r.type = :type)",
            nativeQuery = true)
    List<ReactionCountMismatchProjection> findDislikeCountMismatches(@Param("type") String type);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    void increaseCommentCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.commentCount = p.commentCount - 1 WHERE p.id = :postId AND p.commentCount > 0")
    void decreaseCommentCount(@Param("postId") Long postId);
}
