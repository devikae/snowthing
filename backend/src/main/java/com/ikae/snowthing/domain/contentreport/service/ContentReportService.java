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
import com.ikae.snowthing.domain.message.entity.DirectMessage;
import com.ikae.snowthing.domain.message.repository.DirectMessageRepository;
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
    private final DirectMessageRepository directMessageRepository;
    private final Clock clock;

    @Transactional
    public void create(
            ContentReportCreateRequest request, CustomUserDetails user, String clientIp) {
        // 비회원도 신고할 수 있다. 회원은 회원 기준, 비회원은 IP 기준으로 중복을 막는다.
        Member reporter =
                user == null
                        ? null
                        : memberRepository
                                .findByEmail(user.getUsername())
                                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Long targetId =
                switch (request.targetType()) {
                    case POST -> visiblePost(request.targetId(), user).getId();
                    case COMMENT -> visibleComment(request.targetId(), user).getId();
                    case MESSAGE -> receivedMessage(request.targetId(), reporter).getId();
                };
        String detail =
                request.detail() == null || request.detail().isBlank()
                        ? null
                        : request.detail().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<ContentReport> existing =
                reporter != null
                        ? reportRepository.findByTargetTypeAndTargetIdAndReporter_Id(
                                request.targetType(), targetId, reporter.getId())
                        : reportRepository
                                .findFirstByTargetTypeAndTargetIdAndReporterIsNullAndReporterIp(
                                        request.targetType(), targetId, clientIp);
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
                            clientIp,
                            request.reason(),
                            detail,
                            now));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_DUPLICATE, exception);
        }
    }

    /**
     * 쪽지는 받은 회원만 신고한다. 쪽지는 비공개라 비회원은 볼 수 없으므로 로그인을 요구한다. 보낸 회원, 제3자, 내 쪽에서 지운 쪽지는 쪽지 상세와 같이 404로 답해
     * 존재 여부를 숨긴다 (IKA-123).
     */
    private DirectMessage receivedMessage(String rawMessageId, Member reporter) {
        if (reporter == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }
        long messageId;
        try {
            messageId = Long.parseLong(rawMessageId);
        } catch (NumberFormatException exception) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND, exception);
        }
        return directMessageRepository
                .findById(messageId)
                .filter(
                        message ->
                                message.isReceiver(reporter.getId())
                                        && message.isVisibleTo(reporter.getId()))
                .orElseThrow(() -> new CustomException(ErrorCode.CONTENT_REPORT_TARGET_NOT_FOUND));
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
