package com.ikae.snowthing.domain.post.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.post.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

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
    void increaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    void decreaseLikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.dislikeCount = p.dislikeCount + 1 WHERE p.id = :postId")
    void increaseDislikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.dislikeCount = p.dislikeCount - 1 WHERE p.id = :postId AND p.dislikeCount > 0")
    void decreaseDislikeCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    void increaseCommentCount(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "UPDATE Post p SET p.commentCount = p.commentCount - 1 WHERE p.id = :postId AND p.commentCount > 0")
    void decreaseCommentCount(@Param("postId") Long postId);
}
