package com.ikae.snowthing.domain.comment.service;

import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
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
                                        .findById(request.parentId())
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
                                commentRepository.countByParentIdAndIsDeletedFalse(rootCommentId);
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
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        List<Comment> comments = commentRepository.findByPostIdWithMember(post.getId());

        Map<Long, CommentResponse> map = new LinkedHashMap<>();
        for (Comment comment : comments) {
            map.put(comment.getId(), CommentResponse.from(comment));
        }

        List<CommentResponse> rootComments = new ArrayList<>();
        for (CommentResponse dto : map.values()) {
            if (dto.parentId() == null) {
                rootComments.add(dto);
            } else {
                CommentResponse parentDto = map.get(dto.parentId());
                if (parentDto != null) {
                    parentDto.children().add(dto);
                }
            }
        }

        return PostCommentListResponse.builder()
                .publicId(postPublicId)
                .totalCommentCount(post.getCommentCount())
                .comments(rootComments)
                .build();
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

        if (comment.isAnonymous()) {
            if (userDetails != null
                    && comment.getMember() != null
                    && comment.getMember().getPublicId().equals(userDetails.getPublicId())) {
                return;
            }

            if (anonymousPassword == null
                    || !passwordEncoder.matches(
                            anonymousPassword, comment.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
            return;
        }

        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        boolean isWriter =
                comment.getMember() != null
                        && comment.getMember().getPublicId().equals(userDetails.getPublicId());

        if (!isWriter) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
    }
}
