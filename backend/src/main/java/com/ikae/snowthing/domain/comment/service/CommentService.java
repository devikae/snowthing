package com.ikae.snowthing.domain.comment.service;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.comment.repository.CommentRepositoryCustom.ReplyStats;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService.CommentViewer;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.moderation.service.ModerationEnqueuer;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.InquiryAccess;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final long MAX_REPLY_COUNT = 100L;
    private static final int DEFAULT_READ_SIZE = 20;
    private static final int MAX_READ_SIZE = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final MarketListingRepository marketListingRepository;
    private final SystemConfigService systemConfigService;
    private final PasswordEncoder passwordEncoder;
    private final CommentCommandService commentCommandService;
    private final com.ikae.snowthing.domain.search.service.SearchOutboxWriter searchOutbox;
    private final ChatIpBanService chatIpBanService;
    private final ContentFilterService contentFilter;
    private final ModerationEnqueuer moderation;

    public CommentResponse createComment(
            String postPublicId,
            CommentCreateRequest request,
            CustomUserDetails userDetails,
            String clientIp) {
        if (!isAdmin(userDetails)) {
            chatIpBanService.requireWritable(clientIp);
        }
        // 트랜잭션 밖이라 카테고리와 작성자를 함께 읽어 둔다. 작성 트랜잭션에서 한 번 더 검사한다.
        Post targetPost =
                postRepository
                        .findWithMemberAndCategoryByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        validateMarketAccess(targetPost, userDetails, request.isAnonymous());
        InquiryAccess.requireReadable(targetPost, userDetails);
        Member member = null;
        String encodedPassword = null;

        if (request.isAnonymous()) {
            if (userDetails != null) {
                member =
                        memberRepository
                                .findByPublicId(userDetails.getPublicId())
                                .orElseThrow(
                                        () -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
                if (hasAnonymousPassword(request.anonymousPassword())) {
                    throw new CustomAuthException(ErrorCode.INVALID_INPUT);
                }
            } else {
                if (!hasAnonymousPassword(request.anonymousPassword())) {
                    throw new CustomAuthException(ErrorCode.INVALID_INPUT);
                }
                encodedPassword = passwordEncoder.encode(request.anonymousPassword());
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
            }
            member =
                    memberRepository
                            .findByPublicId(userDetails.getPublicId())
                            .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
        }

        final Member finalMember = member;
        final String finalEncodedPassword = encodedPassword;

        return commentCommandService.createComment(
                postPublicId, request, finalMember, finalEncodedPassword, userDetails, clientIp);
    }

    @Transactional(readOnly = true)
    public PostCommentListResponse getCommentsByPost(String postPublicId) {
        return getCommentsByPost(postPublicId, null, DEFAULT_READ_SIZE);
    }

    @Transactional(readOnly = true)
    public PostCommentListResponse getCommentsByPost(String postPublicId, Long cursor, int size) {
        return getCommentsByPost(postPublicId, cursor, size, null);
    }

    @Transactional(readOnly = true)
    public PostCommentListResponse getCommentsByPost(
            String postPublicId, Long cursor, int size, CustomUserDetails userDetails) {
        return getCommentsByPost(postPublicId, cursor, size, userDetails, null);
    }

    /**
     * @param viewerIp 금칙어 보류 댓글의 비회원 작성자를 알아볼 때 쓴다 (IKA-96)
     */
    @Transactional(readOnly = true)
    public PostCommentListResponse getCommentsByPost(
            String postPublicId,
            Long cursor,
            int size,
            CustomUserDetails userDetails,
            String viewerIp) {
        validateReadSize(size);
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        validatePostVisibility(post, userDetails, viewerIp);
        validateMarketRead(post, userDetails);
        InquiryAccess.requireReadable(post, userDetails);

        if (cursor != null && !commentRepository.existsRootCursor(post.getId(), cursor)) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        CommentViewer viewer = contentFilter.commentViewer(userDetails, viewerIp);
        List<CommentResponse> fetched =
                commentRepository.findRootComments(post.getId(), cursor, size + 1, viewer);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> roots = new ArrayList<>(hasNext ? fetched.subList(0, size) : fetched);
        List<Long> rootIds = roots.stream().map(CommentResponse::commentId).toList();
        Map<Long, ReplyStats> replyStats = commentRepository.findReplyStats(rootIds, viewer);
        Map<Long, List<CommentResponse>> previews =
                commentRepository.findTopReplyPreviews(rootIds, viewer);
        List<CommentResponse> comments =
                roots.stream()
                        .map(
                                root -> {
                                    ReplyStats stat =
                                            replyStats.getOrDefault(
                                                    root.commentId(), new ReplyStats(0, 0));
                                    List<CommentResponse> rootPreviews =
                                            previews.getOrDefault(root.commentId(), List.of());
                                    return root.withReplyInfo(
                                            stat.totalCount(), stat.totalCount() > 5, rootPreviews);
                                })
                        .map(
                                comment ->
                                        comment.withViewerPermissions(
                                                userDetails == null
                                                        ? null
                                                        : userDetails.getPublicId(),
                                                isAdmin(userDetails)))
                        .toList();
        Long nextCursor = hasNext && !comments.isEmpty() ? comments.getLast().commentId() : null;
        return new PostCommentListResponse(
                postPublicId, post.getCommentCount(), comments, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public CommentReplyListResponse getCommentReplies(Long commentId, Long cursor, int size) {
        return getCommentReplies(commentId, cursor, size, null);
    }

    @Transactional(readOnly = true)
    public CommentReplyListResponse getCommentReplies(
            Long commentId, Long cursor, int size, CustomUserDetails userDetails) {
        return getCommentReplies(commentId, cursor, size, userDetails, null);
    }

    @Transactional(readOnly = true)
    public CommentReplyListResponse getCommentReplies(
            Long commentId, Long cursor, int size, CustomUserDetails userDetails, String viewerIp) {
        validateReadSize(size);
        Comment root =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));

        Post post =
                postRepository
                        .findById(root.getPost().getId())
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        validatePostVisibility(post, userDetails, viewerIp);
        validateMarketRead(post, userDetails);
        InquiryAccess.requireReadable(post, userDetails);

        if (root.getParent() != null) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }

        if (cursor != null && !commentRepository.existsReplyCursor(commentId, cursor)) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        CommentViewer viewer = contentFilter.commentViewer(userDetails, viewerIp);
        List<CommentResponse> fetched =
                commentRepository.findReplies(commentId, cursor, size + 1, viewer);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> replies =
                (hasNext ? fetched.subList(0, size) : fetched)
                        .stream()
                                .map(
                                        reply ->
                                                reply.withViewerPermissions(
                                                        userDetails == null
                                                                ? null
                                                                : userDetails.getPublicId(),
                                                        isAdmin(userDetails)))
                                .toList();
        Long nextCursor = hasNext && !replies.isEmpty() ? replies.getLast().commentId() : null;
        return new CommentReplyListResponse(
                commentId,
                commentRepository.countReplies(commentId, viewer),
                replies,
                nextCursor,
                hasNext);
    }

    /**
     * 관리자는 가린 글도 상세에서 보므로 댓글도 함께 읽는다. 글 상세 조회와 같은 기준이다 (IKA-89). 금칙어 보류 글은 작성자 본인에게도 공개 글처럼 댓글을 보여
     * 준다 (IKA-96).
     */
    private void validatePostVisibility(Post post, CustomUserDetails viewer, String viewerIp) {
        if (post == null || post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        if (post.getStatus() != PostStatus.NORMAL
                && !isAdmin(viewer)
                && !contentFilter.canSeeHeldPost(post, viewer, viewerIp)) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void validateReadSize(int size) {
        if (size < 1 || size > MAX_READ_SIZE) {
            throw new CustomAuthException(ErrorCode.COMMENT_INVALID_PAGE_SIZE);
        }
    }

    @Transactional
    public CommentUpdateResponse updateComment(
            Long commentId, CommentUpdateRequest request, CustomUserDetails userDetails) {
        Comment comment =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted() && !comment.isFilterHeld()) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        validateMarketAccess(comment.getPost(), userDetails, false);

        validateUpdatePermission(comment, request.anonymousPassword(), userDetails);

        comment.updateContent(request.content());
        // 아래 보류 처리의 벌크 갱신이 영속성 컨텍스트를 비우므로 글과 회원을 읽는 적재를 먼저 한다.
        moderation.commentWritten(comment);
        applyFilterAfterEdit(comment, userDetails);
        commentRepository.flush();
        searchOutbox.commentChanged(comment, comment.isFilterHeld());

        return new CommentUpdateResponse(
                comment.getId(), comment.getContent(), comment.getUpdatedAt());
    }

    /**
     * 고친 내용을 다시 검사한다 (IKA-96). 금칙어가 새로 들어가면 보류하고, 보류 댓글을 깨끗하게 고치면 다시 공개한다. 공개할 때 보류 중에 세지 않았던 댓글 수를
     * 하나 올린다.
     */
    private void applyFilterAfterEdit(Comment comment, CustomUserDetails editor) {
        Optional<String> matched = contentFilter.inspect(editor, comment.getContent());
        if (matched.isPresent()) {
            boolean newlyHeld = !comment.isFilterHeld();
            if (newlyHeld) {
                comment.holdByFilter(contentFilter.now());
            }
            contentFilter.recordCommentHold(comment, matched.get());
            if (newlyHeld) {
                // 영속성 컨텍스트를 비우는 벌크 갱신이라 마지막에 둔다.
                postRepository.decreaseCommentCount(comment.getPost().getId());
            }
            return;
        }
        if (comment.isFilterHeld()) {
            comment.releaseFilterHold();
            contentFilter.clearHold(FilterTargetType.COMMENT, comment.getId());
            postRepository.increaseCommentCount(comment.getPost().getId());
        }
    }

    private void validateUpdatePermission(
            Comment comment, String anonymousPassword, CustomUserDetails userDetails) {
        if (comment.getMember() != null) {
            if (isWriter(comment, userDetails)) {
                return;
            }
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        if (!comment.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        if (!hasAnonymousPassword(anonymousPassword)
                || comment.getAnonymousPassword() == null
                || !passwordEncoder.matches(anonymousPassword, comment.getAnonymousPassword())) {
            throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
        }
    }

    @Transactional
    public void deleteComment(
            Long commentId, String anonymousPassword, CustomUserDetails userDetails) {
        Comment comment =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.isDeleted() && !comment.isFilterHeld()) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        validateMarketAccess(comment.getPost(), userDetails, false);

        validateDeletePermission(comment, anonymousPassword, userDetails);

        if (comment.isFilterHeld()) {
            // 이미 삭제 상태로 숨겨 둔 댓글이라 보류만 끝낸다. 검색에는 처음부터 없다.
            comment.rejectFilterHold();
            contentFilter.clearHold(FilterTargetType.COMMENT, comment.getId());
            return;
        }
        if (commentRepository.softDeleteIfActive(commentId, LocalDateTime.now()) > 0) {
            searchOutbox.commentChanged(comment, true);
        }
    }

    private void validateDeletePermission(
            Comment comment, String anonymousPassword, CustomUserDetails userDetails) {
        if (isAdmin(userDetails)) {
            return;
        }

        if (comment.getMember() != null) {
            if (isWriter(comment, userDetails)) {
                return;
            }
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        if (!comment.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        if (!hasAnonymousPassword(anonymousPassword)
                || comment.getAnonymousPassword() == null
                || !passwordEncoder.matches(anonymousPassword, comment.getAnonymousPassword())) {
            throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
        }
    }

    private boolean isWriter(Comment comment, CustomUserDetails userDetails) {
        return userDetails != null
                && comment.getMember().getPublicId().equals(userDetails.getPublicId());
    }

    /** 판매글 댓글 조회는 관리자가 비회원 열람을 허용했을 때만 비회원에게 연다. */
    private void validateMarketRead(Post post, CustomUserDetails userDetails) {
        if (userDetails != null || systemConfigService.isMarketGuestAccessEnabled()) {
            return;
        }
        if (marketListingRepository.existsByPostId(post.getId())) {
            throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
        }
    }

    private void validateMarketAccess(
            Post post, CustomUserDetails userDetails, boolean anonymousRequested) {
        if (!marketListingRepository.existsByPostId(post.getId())) {
            return;
        }
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
        }
        if (anonymousRequested) {
            throw new CustomAuthException(ErrorCode.MARKET_ANONYMOUS_COMMENT_NOT_ALLOWED);
        }
    }

    private boolean hasAnonymousPassword(String anonymousPassword) {
        return anonymousPassword != null && !anonymousPassword.isBlank();
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null
                && userDetails.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
