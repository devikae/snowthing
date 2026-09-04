package com.ikae.snowthing.domain.comment.repository;

import java.util.List;
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

    long countByParentIdAndIsDeletedFalse(Long parentId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c.id FROM Comment c WHERE c.parent.id = :parentId AND c.isDeleted = false")
    List<Long> findActiveReplyIdsForUpdate(@Param("parentId") Long parentId);
}
