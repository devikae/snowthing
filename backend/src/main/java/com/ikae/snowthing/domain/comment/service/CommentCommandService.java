package com.ikae.snowthing.domain.comment.service;

import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.dto.CommentCreateRequest;
import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.InquiryAccess;
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
    private final MarketListingRepository marketListingRepository;
    private final com.ikae.snowthing.domain.search.service.SearchOutboxWriter searchOutbox;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentFilterService contentFilter;

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
        if (post.isDeleted()
                || (post.getStatus() != PostStatus.NORMAL
                        && !contentFilter.canSeeHeldPost(post, userDetails, clientIp))) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        if (marketListingRepository.existsByPostId(post.getId())) {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
            }
            if (request.isAnonymous()) {
                throw new CustomAuthException(ErrorCode.MARKET_ANONYMOUS_COMMENT_NOT_ALLOWED);
            }
        }
        InquiryAccess.requireReadable(post, userDetails);
        if (PostCategoryCodes.isInquiry(post) && request.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
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
        // 금칙어가 걸리면 저장과 응답은 평소와 같고, 다른 사람에게 보이지 않게 보류한다 (IKA-96).
        // 보류 댓글은 검색 색인, 알림, 댓글 수에 넣지 않는다. 관리자가 공개하면 그때 넣는다.
        Optional<String> matched = contentFilter.inspect(userDetails, request.content());
        matched.ifPresent(ignored -> comment.holdByFilter(contentFilter.now()));
        Comment saved = commentRepository.save(comment);
        CommentResponse response =
                CommentResponse.from(saved)
                        .withViewerPermissions(
                                userDetails == null ? null : userDetails.getPublicId());
        if (matched.isPresent()) {
            contentFilter.recordCommentHold(saved, matched.get());
            return response;
        }
        searchOutbox.commentChanged(saved, false);
        eventPublisher.publishEvent(CommentCreatedEvent.from(saved));
        postRepository.increaseCommentCount(post.getId());
        return response;
    }
}
