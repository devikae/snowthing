package com.ikae.snowthing.domain.contentreport.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportPageResponse;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse;
import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportResponse.TargetState;
import com.ikae.snowthing.domain.contentreport.dto.ModerationActionRequest;
import com.ikae.snowthing.domain.contentreport.entity.ContentModerationAction;
import com.ikae.snowthing.domain.contentreport.entity.ContentReport;
import com.ikae.snowthing.domain.contentreport.entity.ModerationActionType;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;
import com.ikae.snowthing.domain.contentreport.repository.ContentModerationActionRepository;
import com.ikae.snowthing.domain.contentreport.repository.ContentReportRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContentModerationService {

    private static final int PAGE_SIZE = 30;
    private static final int MAX_HOLD_DAYS = 30;
    // 복구 작업이 매시간 돌아서 늦어져도 법정 상한 30일을 넘지 않게 여유를 둔다.
    private static final int HOLD_MARGIN_HOURS = 2;
    private static final int CONTENT_LENGTH = 2000;
    private static final int PREVIEW_LENGTH = 200;
    private static final int EXPIRY_BATCH_SIZE = 100;
    private static final String ACTOR_NAME = "SnowThing 운영팀";

    private final ContentReportRepository reportRepository;
    private final ContentModerationActionRepository actionRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final NotificationRepository notificationRepository;
    private final SearchOutboxWriter searchOutbox;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminContentReportPageResponse list(ReportStatus status, int page) {
        if (page < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Page<ContentReport> reports =
                reportRepository.findByStatus(
                        status,
                        PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "id")));
        // 미리 읽어 영속성 컨텍스트에 올려 두면 행마다 다시 조회하지 않는다.
        postRepository.findAllById(targetIds(reports.getContent(), ReportTargetType.POST));
        commentRepository.findAllById(targetIds(reports.getContent(), ReportTargetType.COMMENT));
        return new AdminContentReportPageResponse(
                reports.getContent().stream().map(this::toResponse).toList(),
                page,
                reports.getTotalPages(),
                reports.getTotalElements());
    }

    /** 같은 대상의 대기 신고를 모두 처리하고 신고자와 작성자에게 알린다. */
    @Transactional
    public void apply(Long reportId, ModerationActionRequest request, String adminEmail) {
        ContentReport report =
                reportRepository
                        .findById(reportId)
                        .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_NOT_FOUND));
        Optional<ModerationTarget> found = loadTarget(report.getTargetType(), report.getTargetId());
        Member admin =
                memberRepository
                        .findByEmail(adminEmail)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        ModerationActionType action = request.action();
        LocalDateTime expiresAt = null;
        // 작성자가 지운 글은 조회되지 않는다. 대상이 없으면 기각만 받아 대기 신고를 정리한다.
        requireState(found.isPresent() || action == ModerationActionType.DISMISS);
        ModerationTarget target = found.orElse(null);

        switch (action) {
            case TEMPORARY_HOLD -> {
                requireState(target.isPublic());
                int days = request.holdDays() == null ? MAX_HOLD_DAYS : request.holdDays();
                expiresAt = now.plusDays(days).minusHours(HOLD_MARGIN_HOURS);
                hold(target, now);
            }
            case RESTORE -> {
                requireState(target.isHeld());
                restore(target);
                releaseHold(report.getTargetType(), report.getTargetId(), now);
            }
            case DELETE -> {
                requireState(!target.isDeleted());
                delete(target);
                releaseHold(report.getTargetType(), report.getTargetId(), now);
            }
            case DISMISS -> requireState(target == null || !target.isHeld());
        }

        actionRepository.save(
                new ContentModerationAction(
                        report.getTargetType(),
                        report.getTargetId(),
                        action,
                        request.memo(),
                        admin,
                        expiresAt,
                        now));

        List<ContentReport> pending =
                reportRepository.findByTargetTypeAndTargetIdAndStatus(
                        report.getTargetType(), report.getTargetId(), ReportStatus.PENDING);
        pending.forEach(item -> item.resolve(now));
        if (target != null) {
            // 임시조치 때 이미 처리된 신고의 신고자도 이후 복구와 삭제 결과를 받아야 한다.
            notifyParties(
                    target,
                    action,
                    action == ModerationActionType.DISMISS
                            ? pending
                            : reportRepository.findByTargetTypeAndTargetId(
                                    report.getTargetType(), report.getTargetId()));
        }
    }

    /** 기간이 지난 임시조치를 복구하고 복구한 건수를 돌려준다. 그 사이 삭제된 대상은 해제 시각만 기록한다. */
    @Transactional
    public int releaseExpiredHolds() {
        LocalDateTime now = LocalDateTime.now(clock);
        int restored = 0;
        for (ContentModerationAction hold :
                actionRepository.findExpiredHolds(now, PageRequest.of(0, EXPIRY_BATCH_SIZE))) {
            ModerationTarget target =
                    loadTarget(hold.getTargetType(), hold.getTargetId()).orElse(null);
            hold.release(now);
            if (target == null || !target.isHeld()) {
                continue;
            }
            restore(target);
            actionRepository.save(
                    new ContentModerationAction(
                            hold.getTargetType(),
                            hold.getTargetId(),
                            ModerationActionType.RESTORE,
                            "임시조치 기간 만료",
                            null,
                            null,
                            now));
            notifyParties(
                    target,
                    ModerationActionType.RESTORE,
                    reportRepository.findByTargetTypeAndTargetId(
                            hold.getTargetType(), hold.getTargetId()));
            restored++;
        }
        return restored;
    }

    private void hold(ModerationTarget target, LocalDateTime now) {
        if (target.comment() != null) {
            target.comment().holdForModeration(now);
            commentRepository.saveAndFlush(target.comment());
            searchOutbox.commentChanged(target.comment(), true);
            notificationRepository.clearPreviewByCommentId(target.comment().getId());
            return;
        }
        target.post().changeStatus(PostStatus.TEMPORARY_HOLD);
        searchOutbox.postChanged(target.post());
    }

    private void restore(ModerationTarget target) {
        if (target.comment() != null) {
            target.comment().releaseModerationHold();
            commentRepository.saveAndFlush(target.comment());
            searchOutbox.commentChanged(target.comment(), false);
            return;
        }
        target.post().changeStatus(PostStatus.NORMAL);
        searchOutbox.postChanged(target.post());
    }

    private void delete(ModerationTarget target) {
        if (target.comment() != null) {
            target.comment().softDelete();
            commentRepository.saveAndFlush(target.comment());
            searchOutbox.commentChanged(target.comment(), true);
            notificationRepository.clearPreviewByCommentId(target.comment().getId());
            return;
        }
        target.post().softDelete();
        searchOutbox.postChanged(target.post());
    }

    private void releaseHold(ReportTargetType type, Long targetId, LocalDateTime now) {
        actionRepository.findActiveHolds(type, targetId).forEach(hold -> hold.release(now));
    }

    private void requireState(boolean allowed) {
        if (!allowed) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_ACTION_INVALID);
        }
    }

    /**
     * 글은 {@code @SQLRestriction}으로 삭제되면 조회되지 않는다. 댓글의 글 프록시를 그대로 읽으면 예외가 나므로 ID로 다시 찾는다. 프록시의 ID
     * 조회는 쿼리를 만들지 않는다.
     */
    private Optional<ModerationTarget> loadTarget(ReportTargetType type, Long targetId) {
        if (type == ReportTargetType.COMMENT) {
            return commentRepository
                    .findById(targetId)
                    .flatMap(
                            comment ->
                                    postRepository
                                            .findById(comment.getPost().getId())
                                            .map(post -> new ModerationTarget(post, comment)));
        }
        return postRepository.findById(targetId).map(post -> new ModerationTarget(post, null));
    }

    /** 작성자에게 신고자를 알리지 않는다. 비회원 글은 받을 사람이 없어 게시물 자리의 안내로 갈음한다. */
    private void notifyParties(
            ModerationTarget target, ModerationActionType action, List<ContentReport> reports) {
        Map<Long, Notification> notifications = new LinkedHashMap<>();
        for (ContentReport report : reports) {
            addNotification(notifications, report.getReporter(), target, reporterMessage(action));
        }
        String authorMessage = authorMessage(target, action);
        if (target.author() != null && authorMessage != null) {
            addNotification(notifications, target.author(), target, authorMessage);
        }
        notificationRepository.saveAll(new ArrayList<>(notifications.values()));
    }

    private void addNotification(
            Map<Long, Notification> notifications,
            Member receiver,
            ModerationTarget target,
            String message) {
        if (receiver.getStatus() == MemberStatus.WITHDRAWN) {
            return;
        }
        Post post = target.post();
        notifications.put(
                receiver.getId(),
                Notification.create(
                        receiver,
                        NotificationType.MODERATION_RESULT,
                        post.getId(),
                        post.getPublicId(),
                        post.getCategory().getCode(),
                        target.commentId(),
                        ACTOR_NAME,
                        message));
    }

    private String reporterMessage(ModerationActionType action) {
        return switch (action) {
            case TEMPORARY_HOLD -> "신고한 내용을 확인해 임시조치했습니다.";
            case DELETE -> "신고한 내용을 확인해 삭제했습니다.";
            case RESTORE -> "신고한 내용을 확인했고 게시물을 복구했습니다.";
            case DISMISS -> "신고한 내용을 확인했고 별도 조치는 하지 않았습니다.";
        };
    }

    private String authorMessage(ModerationTarget target, ModerationActionType action) {
        String label = target.label();
        return switch (action) {
            case TEMPORARY_HOLD -> "내 " + label + "이 신고되어 임시조치되었습니다. 이의는 운영팀 메일로 보내 주세요.";
            case DELETE -> "내 " + label + "이 운영 정책에 따라 삭제되었습니다. 이의는 운영팀 메일로 보내 주세요.";
            case RESTORE -> "임시조치된 내 " + label + "이 복구되었습니다.";
            case DISMISS -> null;
        };
    }

    private List<Long> targetIds(List<ContentReport> reports, ReportTargetType type) {
        return reports.stream()
                .filter(report -> report.getTargetType() == type)
                .map(ContentReport::getTargetId)
                .distinct()
                .toList();
    }

    private AdminContentReportResponse toResponse(ContentReport report) {
        ModerationTarget target =
                loadTarget(report.getTargetType(), report.getTargetId()).orElse(null);
        Post post = target == null ? null : target.post();
        return new AdminContentReportResponse(
                report.getId(),
                report.getTargetType(),
                targetState(target),
                target == null ? null : preview(target),
                target == null ? null : fullContent(target),
                post == null ? null : post.getPublicId(),
                post == null ? null : post.getCategory().getCode(),
                report.getReason(),
                report.getDetail(),
                report.getReporter().getNickname(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getResolvedAt());
    }

    private TargetState targetState(ModerationTarget target) {
        if (target == null || target.isDeleted()) {
            return TargetState.DELETED;
        }
        if (target.isHeld()) {
            return TargetState.HELD;
        }
        return target.isPublic() ? TargetState.NORMAL : TargetState.HIDDEN;
    }

    /** 임시조치된 판매글과 카풀은 상세 화면으로 볼 수 없어 관리자 목록에 본문을 담는다. */
    private String fullContent(ModerationTarget target) {
        String text =
                target.comment() != null
                        ? target.comment().getContent()
                        : target.post().getContent();
        return text.length() <= CONTENT_LENGTH ? text : text.substring(0, CONTENT_LENGTH);
    }

    private String preview(ModerationTarget target) {
        String text =
                target.comment() != null ? target.comment().getContent() : target.post().getTitle();
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH);
    }
}
