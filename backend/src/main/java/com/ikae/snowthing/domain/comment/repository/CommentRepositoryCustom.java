package com.ikae.snowthing.domain.comment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ikae.snowthing.domain.comment.dto.CommentResponse;

public interface CommentRepositoryCustom {

    record CursorPosition(LocalDateTime createdAt, Long commentId) {}

    Optional<CursorPosition> findRootCursor(Long postId, Long cursorId);

    Optional<CursorPosition> findReplyCursor(Long rootCommentId, Long cursorId);

    List<CommentResponse> findRootComments(Long postId, CursorPosition cursor, int fetchSize);

    Map<Long, List<CommentResponse>> findTopReplyPreviews(List<Long> rootCommentIds);

    List<CommentResponse> findReplies(Long rootCommentId, CursorPosition cursor, int fetchSize);

    long countActiveReplies(Long rootCommentId);
}
