package com.ikae.snowthing.domain.contentreport.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.contentreport.dto.ContentReportCreateRequest;
import com.ikae.snowthing.domain.contentreport.entity.ContentReport;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.repository.ContentReportRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.InquiryAccess;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContentReportService {

    private final ContentReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public void create(ContentReportCreateRequest request, CustomUserDetails user) {
        Member reporter =
                memberRepository
                        .findByEmail(user.getUsername())
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Long targetId =
                switch (request.targetType()) {
                    case POST -> visiblePost(request.targetId(), user).getId();
                    case COMMENT -> visibleComment(request.targetId(), user).getId();
                };
        String detail =
                request.detail() == null || request.detail().isBlank()
                        ? null
                        : request.detail().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<ContentReport> existing =
                reportRepository.findByTargetTypeAndTargetIdAndReporter_Id(
                        request.targetType(), targetId, reporter.getId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() == ReportStatus.PENDING) {
                throw new CustomException(ErrorCode.CONTENT_REPORT_DUPLICATE);
            }
            existing.get().reopen(request.reason(), detail, now);
            return;
        }
        try {
            reportRepository.saveAndFlush(
                    new ContentReport(
                            request.targetType(),
                            targetId,
                            reporter,
                            request.reason(),
                            detail,
                            now));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_DUPLICATE, exception);
        }
    }

    private Post visiblePost(String publicId, CustomUserDetails user) {
        Post post =
                postRepository
                        .findByPublicId(publicId)
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
        requireVisible(post, user);
        return post;
    }

    private Comment visibleComment(String rawCommentId, CustomUserDetails user) {
        long commentId;
        try {
            commentId = Long.parseLong(rawCommentId);
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND, exception);
        }
        Comment comment =
                commentRepository
                        .findById(commentId)
                        .filter(found -> !found.isDeleted())
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
        // 삭제된 글은 @SQLRestriction으로 조회되지 않아 프록시를 바로 읽으면 예외가 난다.
        Post post =
                postRepository
                        .findById(comment.getPost().getId())
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
        requireVisible(post, user);
        return comment;
    }

    private void requireVisible(Post post, CustomUserDetails user) {
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND);
        }
        InquiryAccess.requireReadable(post, user);
    }
}
