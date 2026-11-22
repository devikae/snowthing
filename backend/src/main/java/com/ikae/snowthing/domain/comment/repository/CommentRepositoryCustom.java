package com.ikae.snowthing.domain.comment.repository;

import java.util.List;
import java.util.Map;

import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService.CommentViewer;

/**
 * 댓글 목록 조회. 목록 메서드는 보는 사람(viewer)을 받는다. 금칙어 보류 댓글은 작성자 본인에게만 평소처럼 나오고 다른 사람에게는 행 자체가 빠진다 (IKA-96).
 */
public interface CommentRepositoryCustom {

    boolean existsRootCursor(Long postId, Long cursorId);

    boolean existsReplyCursor(Long rootCommentId, Long cursorId);

    List<CommentResponse> findRootComments(
            Long postId, Long cursorId, int fetchSize, CommentViewer viewer);

    record ReplyStats(long activeCount, long totalCount) {}

    Map<Long, ReplyStats> findReplyStats(List<Long> rootCommentIds, CommentViewer viewer);

    Map<Long, List<CommentResponse>> findTopReplyPreviews(
            List<Long> rootCommentIds, CommentViewer viewer);

    List<CommentResponse> findReplies(
            Long rootCommentId, Long cursorId, int fetchSize, CommentViewer viewer);

    long countActiveReplies(Long rootCommentId);

    long countReplies(Long rootCommentId, CommentViewer viewer);
}
