package com.ikae.snowthing.domain.comment.repository;

import java.util.List;
import java.util.Map;

import com.ikae.snowthing.domain.comment.dto.CommentResponse;

public interface CommentRepositoryCustom {

    boolean existsRootCursor(Long postId, Long cursorId);

    boolean existsReplyCursor(Long rootCommentId, Long cursorId);

    List<CommentResponse> findRootComments(Long postId, Long cursorId, int fetchSize);

    record ReplyStats(long activeCount, long totalCount) {}

    Map<Long, ReplyStats> findReplyStats(List<Long> rootCommentIds);

    Map<Long, List<CommentResponse>> findTopReplyPreviews(List<Long> rootCommentIds);

    List<CommentResponse> findReplies(Long rootCommentId, Long cursorId, int fetchSize);

    long countActiveReplies(Long rootCommentId);

    long countReplies(Long rootCommentId);
}
