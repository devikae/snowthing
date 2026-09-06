package com.ikae.snowthing.domain.comment.service;

import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.comment.repository.CommentRepositoryCustom.ReplyStats;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final CommentCommandService commentCommandService;

    public CommentResponse createComment(
            String postPublicId,
            CommentCreateRequest request,
            CustomUserDetails userDetails,
            String clientIp) {
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
                throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
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
        validateReadSize(size);
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        validatePostVisibility(post);

        if (cursor != null && !commentRepository.existsRootCursor(post.getId(), cursor)) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        List<CommentResponse> fetched =
                commentRepository.findRootComments(post.getId(), cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> roots = new ArrayList<>(hasNext ? fetched.subList(0, size) : fetched);
        List<Long> rootIds = roots.stream().map(CommentResponse::commentId).toList();
        Map<Long, ReplyStats> replyStats = commentRepository.findReplyStats(rootIds);
        Map<Long, List<CommentResponse>> previews = commentRepository.findTopReplyPreviews(rootIds);
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
                                                        : userDetails.getPublicId()))
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
        validateReadSize(size);
        Comment root =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));

        Post post =
                postRepository
                        .findById(root.getPost().getId())
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        validatePostVisibility(post);

        if (root.getParent() != null) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (cursor != null && !commentRepository.existsReplyCursor(commentId, cursor)) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        List<CommentResponse> fetched = commentRepository.findReplies(commentId, cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> replies =
                (hasNext ? fetched.subList(0, size) : fetched)
                        .stream()
                                .map(
                                        reply ->
                                                reply.withViewerPermissions(
                                                        userDetails == null
                                                                ? null
                                                                : userDetails.getPublicId()))
                                .toList();
        Long nextCursor = hasNext && !replies.isEmpty() ? replies.getLast().commentId() : null;
        return new CommentReplyListResponse(
                commentId, commentRepository.countReplies(commentId), replies, nextCursor, hasNext);
    }

    private void validatePostVisibility(Post post) {
        if (post == null || post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
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

        if (comment.isDeleted()) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }

        validateUpdatePermission(comment, request.anonymousPassword(), userDetails);

        comment.updateContent(request.content());
        commentRepository.flush();

        return new CommentUpdateResponse(
                comment.getId(), comment.getContent(), comment.getUpdatedAt());
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

        if (comment.isDeleted()) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }

        validateDeletePermission(comment, anonymousPassword, userDetails);

        comment.softDelete();
        postRepository.decreaseCommentCount(comment.getPost().getId());
    }

    private void validateDeletePermission(
            Comment comment, String anonymousPassword, CustomUserDetails userDetails) {
        boolean isAdmin =
                userDetails != null
                        && userDetails.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
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

    private boolean hasAnonymousPassword(String anonymousPassword) {
        return anonymousPassword != null && !anonymousPassword.isBlank();
    }
}
