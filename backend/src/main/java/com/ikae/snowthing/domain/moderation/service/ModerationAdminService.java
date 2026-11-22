package com.ikae.snowthing.domain.moderation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.service.AdminCommentService;
import com.ikae.snowthing.domain.admin.service.AdminPostService;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.domain.moderation.dto.ModerationItem;
import com.ikae.snowthing.domain.moderation.entity.ModerationReviewResult;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository.Row;
import com.ikae.snowthing.domain.moderation.repository.ModerationTargetQueryRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;
import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 자동 검열 검토 대기열 (IKA-80). 숨김(HIDE)과 검토 대기(REVIEW) 판정을 관리자가 처리한다.
 *
 * <ul>
 *   <li>문제없음: 자동 숨김이면 숨김을 푼다. 같은 내용 해시는 다시 숨기지 않는다.
 *   <li>숨김 유지: 자동 숨김은 그대로 둔다. 검토 대기 항목은 이때 숨긴다.
 *   <li>삭제: 대상마다 기존 관리자 삭제 흐름을 탄다.
 * </ul>
 *
 * <p>숨김과 해제, 삭제는 기존 관리자 조치(글 블라인드, 장터 상태, 댓글 블라인드, 설질 제보 상태)를 그대로 불러 검색 반영과 조치 기록이 빠지지 않게 한다. 같은
 * 대상의 아직 검토하지 않은 이전 판정은 함께 닫는다(더 새 판정은 남긴다). 같은 항목을 두 관리자가 동시에 처리하지 않게 판정 줄을 잠그고 읽는다.
 */
@Service
@RequiredArgsConstructor
public class ModerationAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ContentModerationRepository repository;
    private final ModerationTargetQueryRepository targets;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ResortReportRepository resortReportRepository;
    private final AdminPostService adminPostService;
    private final AdminCommentService adminCommentService;
    private final MarketListingService marketListingService;
    private final ResortReportService resortReportService;
    private final AdminActionLogger actionLogger;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminPage<ModerationItem> getQueue(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<ModerationItem> fetched =
                repository.findReviewQueue(safePage * safeSize, safeSize + 1).stream()
                        .map(
                                row ->
                                        ModerationItem.of(
                                                row,
                                                targets.find(row.targetType(), row.targetId())
                                                        .orElse(null)))
                        .toList();
        return AdminPage.of(fetched, safePage, safeSize);
    }

    @Transactional
    public void pass(Long id, CustomUserDetails admin) {
        Row row = lock(id);
        if (row.verdict() == ModerationVerdict.HIDE) {
            unhide(row, admin);
        }
        close(row, ModerationReviewResult.PASS, AdminActionType.MODERATION_PASS, admin);
    }

    @Transactional
    public void keep(Long id, CustomUserDetails admin) {
        Row row = lock(id);
        if (row.verdict() == ModerationVerdict.REVIEW) {
            hide(row, admin);
        }
        close(row, ModerationReviewResult.KEEP, AdminActionType.MODERATION_KEEP, admin);
    }

    @Transactional
    public void delete(Long id, CustomUserDetails admin) {
        Row row = lock(id);
        if (row.targetType().isPost()) {
            Post post = postRepository.findById(row.targetId()).orElse(null);
            if (post != null) {
                adminPostService.deletePost(post.getPublicId(), admin);
            }
        } else if (row.targetType() == ModerationTargetType.COMMENT) {
            Comment comment = commentRepository.findById(row.targetId()).orElse(null);
            if (comment != null && (!comment.isDeleted() || comment.isModerationHeld())) {
                adminCommentService.delete(admin, comment.getId());
            }
        } else {
            ResortReport report = resortReportRepository.findById(row.targetId()).orElse(null);
            if (report != null && !report.isDeleted()) {
                resortReportService.deleteReport(report.getId(), admin);
            }
        }
        close(row, ModerationReviewResult.DELETE, AdminActionType.MODERATION_DELETE, admin);
    }

    /** 자동 숨김을 푼다. 그 사이 다른 조치로 상태가 바뀌었으면(지워짐, 신고 임시조치) 건드리지 않는다. */
    private void unhide(Row row, CustomUserDetails admin) {
        if (row.targetType().isPost()) {
            Post post = postRepository.findById(row.targetId()).orElse(null);
            if (post == null) return;
            if (row.targetType() == ModerationTargetType.MARKET) {
                if (post.getStatus() == PostStatus.HIDDEN) {
                    marketListingService.updateModerationStatus(
                            post.getPublicId(), PostStatus.NORMAL, admin);
                }
            } else if (post.getStatus() == PostStatus.BLOCKED) {
                adminPostService.setBlinded(post.getPublicId(), false);
            }
            return;
        }
        if (row.targetType() == ModerationTargetType.COMMENT) {
            Comment comment = commentRepository.findById(row.targetId()).orElse(null);
            if (comment != null && comment.isModerationHeld()) {
                adminCommentService.setBlinded(admin, comment.getId(), false);
            }
            return;
        }
        ResortReport report = resortReportRepository.findById(row.targetId()).orElse(null);
        if (report != null && report.getStatus() == ResortReportStatus.HIDDEN) {
            resortReportService.updateModerationStatus(
                    report.getId(), ResortReportStatus.NORMAL, admin);
        }
    }

    /** 검토 대기 항목을 숨긴다. 이미 가려졌거나 지워졌으면 그대로 둔다. */
    private void hide(Row row, CustomUserDetails admin) {
        if (row.targetType().isPost()) {
            Post post = postRepository.findById(row.targetId()).orElse(null);
            if (post == null || post.getStatus() != PostStatus.NORMAL) return;
            if (row.targetType() == ModerationTargetType.MARKET) {
                marketListingService.updateModerationStatus(
                        post.getPublicId(), PostStatus.HIDDEN, admin);
            } else {
                adminPostService.setBlinded(post.getPublicId(), true);
            }
            return;
        }
        if (row.targetType() == ModerationTargetType.COMMENT) {
            Comment comment = commentRepository.findById(row.targetId()).orElse(null);
            if (comment != null && !comment.isDeleted()) {
                adminCommentService.setBlinded(admin, comment.getId(), true);
            }
            return;
        }
        ResortReport report = resortReportRepository.findById(row.targetId()).orElse(null);
        if (report != null && report.getStatus() == ResortReportStatus.NORMAL) {
            resortReportService.updateModerationStatus(
                    report.getId(), ResortReportStatus.HIDDEN, admin);
        }
    }

    private void close(
            Row row,
            ModerationReviewResult result,
            AdminActionType action,
            CustomUserDetails admin) {
        repository.markReviewed(
                row, result, admin == null ? null : admin.getMemberId(), LocalDateTime.now(clock));
        actionLogger.record(
                action,
                AdminTargetType.CONTENT_MODERATION,
                row.id(),
                row.targetType() + ":" + row.targetId());
    }

    private Row lock(Long id) {
        if (id == null) {
            throw new CustomException(ErrorCode.MODERATION_NOT_FOUND);
        }
        return repository
                .lockReviewable(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MODERATION_NOT_FOUND));
    }
}
