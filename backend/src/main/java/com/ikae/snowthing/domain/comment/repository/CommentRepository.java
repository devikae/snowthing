package com.ikae.snowthing.domain.comment.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.comment.entity.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long>, CommentRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Comment c WHERE c.id = :commentId")
    Optional<Comment> findByIdForUpdate(@Param("commentId") Long commentId);

    @org.springframework.data.jpa.repository.Modifying(
            flushAutomatically = true,
            clearAutomatically = true)
    @Query(
            "UPDATE Comment c SET c.isDeleted = true, c.deletedAt = :deletedAt "
                    + "WHERE c.id = :commentId AND c.isDeleted = false")
    int softDeleteIfActive(
            @Param("commentId") Long commentId, @Param("deletedAt") LocalDateTime deletedAt);

    long countByParentIdAndIsDeletedFalse(Long parentId);
}
