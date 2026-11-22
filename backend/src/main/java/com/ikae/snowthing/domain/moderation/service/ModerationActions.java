package com.ikae.snowthing.domain.moderation.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;
import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;

import lombok.RequiredArgsConstructor;

/**
 * 자동 숨김 (IKA-80). 대상마다 이미 있는 관리자 가리기 상태를 그대로 쓴다. 새 상태 값은 만들지 않는다.
 *
 * <ul>
 *   <li>게시판 글, 카풀: {@code post.status = BLOCKED} (관리자 블라인드와 같다).
 *   <li>장터: {@code post.status = HIDDEN} (장터 관리 화면의 숨김과 같다).
 *   <li>댓글: {@code moderation_held} + 삭제 상태 (관리자 댓글 블라인드와 같다).
 *   <li>설질 제보: {@code status = HIDDEN} (설질 제보 관리 화면의 숨김과 같다).
 * </ul>
 *
 * <p>AI는 숨기기만 한다. 지우거나 회원을 정지하지 않는다. 관리자 조치 기록에는 남기지 않는다(조치한 사람이 없다). 판정 기록이 content_moderation에
 * 남는다.
 */
@Component
@RequiredArgsConstructor
public class ModerationActions {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ResortReportRepository resortReportRepository;
    private final NotificationRepository notificationRepository;
    private final SearchOutboxWriter searchOutbox;
    private final Clock clock;

    /**
     * 대상을 잠그고 숨긴다. 지금 보이는 상태가 아니거나 판정한 뒤 내용이 바뀌었으면 숨기지 않고 false를 돌려준다.
     *
     * @param expectedHash 판정한 내용의 해시. 판정과 숨김 사이에 작성자가 고쳤으면 새 줄이 따로 판정한다
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean hide(ModerationTargetType type, long targetId, String expectedHash) {
        if (type.isPost()) {
            Post post = postRepository.findByIdForUpdate(targetId).orElse(null);
            if (post == null
                    || post.isDeleted()
                    || post.getStatus() != PostStatus.NORMAL
                    || !expectedHash.equals(
                            ModerationTextRules.contentHash(post.getTitle(), post.getContent()))) {
                return false;
            }
            post.changeStatus(
                    type == ModerationTargetType.MARKET ? PostStatus.HIDDEN : PostStatus.BLOCKED);
            searchOutbox.postChanged(post);
            return true;
        }
        if (type == ModerationTargetType.COMMENT) {
            Comment comment = commentRepository.findByIdForUpdate(targetId).orElse(null);
            if (comment == null
                    || comment.isDeleted()
                    || !expectedHash.equals(
                            ModerationTextRules.contentHash("", comment.getContent()))) {
                return false;
            }
            comment.holdForModeration(LocalDateTime.now(clock));
            commentRepository.saveAndFlush(comment);
            searchOutbox.commentChanged(comment, true);
            // 영속성 컨텍스트를 건드리는 벌크 갱신이라 마지막에 둔다.
            notificationRepository.clearPreviewByCommentId(targetId);
            return true;
        }
        ResortReport report = resortReportRepository.findById(targetId).orElse(null);
        if (report == null
                || report.getStatus() != ResortReportStatus.NORMAL
                || !expectedHash.equals(ModerationTextRules.contentHash("", report.getContent()))) {
            return false;
        }
        report.changeModerationStatus(ResortReportStatus.HIDDEN);
        return true;
    }
}
