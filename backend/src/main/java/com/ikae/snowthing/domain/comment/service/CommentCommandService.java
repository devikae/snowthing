package com.ikae.snowthing.domain.comment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class CommentCommandService {
    private static final long MAX_REPLY_COUNT = 100L;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    @Transactional
    CommentResponse createComment(
            String postPublicId,
            CommentCreateRequest request,
            Member member,
            String encodedPassword,
            CustomUserDetails userDetails,
            String clientIp) {
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
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
                                                    ErrorCode.PARENT_COMMENT_NOT_FOUND));
            if (!requestedParent.getPost().getId().equals(post.getId())) {
                throw new CustomAuthException(ErrorCode.INVALID_COMMENT_PARENT);
            }
            Long rootId = requestedParent.rootParent().getId();
            parent =
                    commentRepository
                            .findByIdForUpdate(rootId)
                            .orElseThrow(
                                    () ->
                                            new CustomAuthException(
                                                    ErrorCode.PARENT_COMMENT_NOT_FOUND));
            if (commentRepository.findActiveReplyIdsForUpdate(rootId).size() >= MAX_REPLY_COUNT) {
                throw new CustomAuthException(ErrorCode.COMMENT_REPLY_LIMIT_EXCEEDED);
            }
        }
        Comment comment =
                Comment.create(
                        post,
                        member,
                        parent,
                        request.content(),
                        clientIp != null ? clientIp : "127.0.0.1",
                        request.isAnonymous(),
                        encodedPassword);
        CommentResponse response =
                CommentResponse.from(commentRepository.save(comment))
                        .withViewerPermissions(
                                userDetails == null ? null : userDetails.getPublicId());
        postRepository.increaseCommentCount(post.getId());
        return response;
    }
}
