package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;
import com.ikae.snowthing.domain.contentreport.repository.ContentModerationActionRepository;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 댓글 블라인드, 해제, 삭제 (IKA-78).
 *
 * <p>블라인드는 신고 임시조치와 같은 {@code moderation_held}를 쓴다. 목록, 검색, AI 조회에서 이미 빠지는 상태라 새 컬럼이 필요 없다. 차이는 만료가
 * 없다는 것이다(임시조치 이력 행을 만들지 않아 만료 스케줄러가 풀지 않는다). 신고 임시조치 중인 댓글은 신고 처리 화면에서만 해제한다.
 */
@Service
@RequiredArgsConstructor
public class AdminCommentService {

    private final CommentRepository commentRepository;
    private final ContentModerationActionRepository moderationActionRepository;
    private final NotificationRepository notificationRepository;
    private final SearchOutboxWriter searchOutbox;
    private final AdminActionLogger actionLogger;

    @Transactional
    public void setBlinded(CustomUserDetails admin, Long commentId, boolean blinded) {
        AdminAccess.require(admin);
        Comment comment = lock(commentId);
        if (comment.isDeleted() && !comment.isModerationHeld()) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (blinded == comment.isModerationHeld()) {
            return;
        }
        if (blinded) {
            comment.holdForModeration(LocalDateTime.now());
            commentRepository.saveAndFlush(comment);
            searchOutbox.commentChanged(comment, true);
            notificationRepository.clearPreviewByCommentId(commentId);
        } else {
            if (!moderationActionRepository
                    .findActiveHolds(ReportTargetType.COMMENT, commentId)
                    .isEmpty()) {
                throw new CustomException(ErrorCode.CONTENT_REPORT_ACTION_INVALID);
            }
            comment.releaseModerationHold();
            commentRepository.saveAndFlush(comment);
            searchOutbox.commentChanged(comment, false);
        }
        actionLogger.record(
                blinded ? AdminActionType.COMMENT_BLIND : AdminActionType.COMMENT_UNBLIND,
                AdminTargetType.COMMENT,
                commentId,
                null);
    }

    /** 보이는 댓글과 가려진 댓글 모두 지울 수 있다. 신고 임시조치가 걸려 있으면 함께 해제해 만료 스케줄러가 되살리지 않게 한다. */
    @Transactional
    public void delete(CustomUserDetails admin, Long commentId) {
        AdminAccess.require(admin);
        Comment comment = lock(commentId);
        if (comment.isDeleted() && !comment.isModerationHeld()) {
            throw new CustomException(ErrorCode.COMMENT_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        comment.softDelete();
        commentRepository.saveAndFlush(comment);
        searchOutbox.commentChanged(comment, true);
        notificationRepository.clearPreviewByCommentId(commentId);
        moderationActionRepository
                .findActiveHolds(ReportTargetType.COMMENT, commentId)
                .forEach(hold -> hold.release(now));
        actionLogger.record(
                AdminActionType.COMMENT_DELETE, AdminTargetType.COMMENT, commentId, null);
    }

    private Comment lock(Long commentId) {
        return commentRepository
                .findByIdForUpdate(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND));
    }
}
