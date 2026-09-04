package com.ikae.snowthing.domain.comment.service;

import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.comment.repository.CommentRepositoryCustom;
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
@Transactional(readOnly = true)
public class CommentService {

    private static final long MAX_REPLY_COUNT = 100L;
    private static final int DEFAULT_READ_SIZE = 20;
    private static final int MAX_READ_SIZE = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public CommentResponse createComment(
            String postPublicId,
            CommentCreateRequest request,
            CustomUserDetails userDetails,
            String clientIp) {
        Member member = null;
        String encodedPassword = null;

        if (request.isAnonymous()) {
            if (userDetails != null) {
                member = memberRepository.findByPublicId(userDetails.getPublicId()).orElse(null);
            }
            if (member == null
                    && (request.anonymousPassword() == null
                            || request.anonymousPassword().isBlank())) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
            if (request.anonymousPassword() != null && !request.anonymousPassword().isBlank()) {
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

        return transactionTemplate.execute(
                status -> {
                    Post post =
                            postRepository
                                    .findByPublicId(postPublicId)
                                    .orElseThrow(
                                            () ->
                                                    new CustomAuthException(
                                                            ErrorCode.POST_NOT_FOUND));

                    if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
                        throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
                    }

                    Comment parent = null;
                    if (request.parentId() != null) {
                        Comment requestedParent =
                                commentRepository
                                        .findByIdForUpdate(request.parentId())
                                        .orElseThrow(
                                                () ->
                                                        new CustomAuthException(
                                                                ErrorCode
                                                                        .PARENT_COMMENT_NOT_FOUND));

                        if (!requestedParent.getPost().getId().equals(post.getId())) {
                            throw new CustomAuthException(ErrorCode.INVALID_COMMENT_PARENT);
                        }

                        Long rootCommentId = requestedParent.rootParent().getId();
                        parent =
                                commentRepository
                                        .findByIdForUpdate(rootCommentId)
                                        .orElseThrow(
                                                () ->
                                                        new CustomAuthException(
                                                                ErrorCode
                                                                        .PARENT_COMMENT_NOT_FOUND));

                        long activeReplyCount =
                                commentRepository.findActiveReplyIdsForUpdate(rootCommentId).size();
                        if (activeReplyCount >= MAX_REPLY_COUNT) {
                            throw new CustomAuthException(ErrorCode.COMMENT_REPLY_LIMIT_EXCEEDED);
                        }
                    }

                    Comment comment =
                            Comment.create(
                                    post,
                                    finalMember,
                                    parent,
                                    request.content(),
                                    clientIp != null ? clientIp : "127.0.0.1",
                                    request.isAnonymous(),
                                    finalEncodedPassword);

                    Comment savedComment = commentRepository.save(comment);
                    postRepository.increaseCommentCount(post.getId());

                    return CommentResponse.from(savedComment);
                });
    }

    public PostCommentListResponse getCommentsByPost(String postPublicId) {
        return getCommentsByPost(postPublicId, null, DEFAULT_READ_SIZE);
    }

    public PostCommentListResponse getCommentsByPost(String postPublicId, Long cursor, int size) {
        validateReadSize(size);
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        CommentRepositoryCustom.CursorPosition cursorPosition =
                cursor == null
                        ? null
                        : commentRepository
                                .findRootCursor(post.getId(), cursor)
                                .orElseThrow(
                                        () -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));
        List<CommentResponse> fetched =
                commentRepository.findRootComments(post.getId(), cursorPosition, size + 1);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> roots = new ArrayList<>(hasNext ? fetched.subList(0, size) : fetched);
        Map<Long, List<CommentResponse>> previews =
                commentRepository.findTopReplyPreviews(
                        roots.stream().map(CommentResponse::commentId).toList());
        List<CommentResponse> comments =
                roots.stream()
                        .map(
                                root ->
                                        root.withPreviewReplies(
                                                previews.getOrDefault(root.commentId(), List.of())))
                        .toList();
        Long nextCursor = hasNext && !comments.isEmpty() ? comments.getLast().commentId() : null;
        return new PostCommentListResponse(
                postPublicId, post.getCommentCount(), comments, nextCursor, hasNext);
    }

    public CommentReplyListResponse getCommentReplies(Long commentId, Long cursor, int size) {
        validateReadSize(size);
        Comment root =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));
        if (root.getParent() != null) {
            throw new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND);
        }
        CommentRepositoryCustom.CursorPosition cursorPosition =
                cursor == null
                        ? null
                        : commentRepository
                                .findReplyCursor(commentId, cursor)
                                .orElseThrow(
                                        () -> new CustomAuthException(ErrorCode.COMMENT_NOT_FOUND));
        List<CommentResponse> fetched =
                commentRepository.findReplies(commentId, cursorPosition, size + 1);
        boolean hasNext = fetched.size() > size;
        List<CommentResponse> replies = List.copyOf(hasNext ? fetched.subList(0, size) : fetched);
        Long nextCursor = hasNext && !replies.isEmpty() ? replies.getLast().commentId() : null;
        return new CommentReplyListResponse(
                commentId,
                commentRepository.countActiveReplies(commentId),
                replies,
                nextCursor,
                hasNext);
    }

    private void validateReadSize(int size) {
        if (size < 1 || size > MAX_READ_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
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
            boolean isWriter =
                    userDetails != null
                            && comment.getMember().getPublicId().equals(userDetails.getPublicId());
            if (!isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
            return;
        }

        if (anonymousPassword == null
                || !passwordEncoder.matches(anonymousPassword, comment.getAnonymousPassword())) {
            throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
        }
    }
}
