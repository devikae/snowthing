package com.ikae.snowthing.domain.comment.repository;

import com.ikae.snowthing.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c LEFT JOIN FETCH c.member WHERE c.post.id = :postId ORDER BY c.createdAt ASC, c.id ASC")
    List<Comment> findByPostIdWithMember(@Param("postId") Long postId);
}
