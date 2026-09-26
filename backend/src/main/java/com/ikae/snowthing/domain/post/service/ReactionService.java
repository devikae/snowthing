package com.ikae.snowthing.domain.post.service;

import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.ReactionCountMismatch;
import com.ikae.snowthing.domain.post.dto.ReactionReconciliationResult;
import com.ikae.snowthing.domain.post.dto.ReactionResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.event.PostReactionEvent;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
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
public class ReactionService {

    private static final String DEFAULT_WRITER_IP = "127.0.0.1";
    private static final String LIKE_APPLIED_MESSAGE = "추천했습니다.";
    private static final String LIKE_ALREADY_APPLIED_MESSAGE = "이미 추천한 게시글입니다.";
    private static final String LIKE_REMOVED_MESSAGE = "추천을 취소했습니다.";
    private static final String LIKE_ALREADY_REMOVED_MESSAGE = "이미 추천하지 않은 상태입니다.";
    private static final String DISLIKE_APPLIED_MESSAGE = "비추천했습니다.";
    private static final String DISLIKE_ALREADY_APPLIED_MESSAGE = "이미 비추천한 게시글입니다.";
    private static final String DISLIKE_REMOVED_MESSAGE = "비추천을 취소했습니다.";
    private static final String DISLIKE_ALREADY_REMOVED_MESSAGE = "이미 비추천하지 않은 상태입니다.";
    private static final int SINGLE_ROW_AFFECTED = 1;

    private final PostRepository postRepository;
    private final PostReactionRepository reactionRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReactionResponse apply(
            String publicId,
            ReactionType type,
            CustomUserDetails userDetails,
            String clientIp,
            String anonymousVoterId) {
        Post post = findAvailablePost(publicId);
        ReactionActor actor = resolveActor(userDetails, clientIp, anonymousVoterId);

        increaseCount(post.getId(), type);
        int inserted = insertReaction(post.getId(), actor, type);
        if (inserted == SINGLE_ROW_AFFECTED) {
            eventPublisher.publishEvent(new PostReactionEvent(post.getId(), type));
        } else {
            decreaseCount(post.getId(), type);
        }

        return response(post.getId(), type, true, inserted == SINGLE_ROW_AFFECTED);
    }

    @Transactional
    public ReactionResponse remove(
            String publicId,
            ReactionType type,
            CustomUserDetails userDetails,
            String clientIp,
            String anonymousVoterId) {
        Post post = findAvailablePost(publicId);
        ReactionActor actor = resolveActor(userDetails, clientIp, anonymousVoterId);

        lockReactionCounter(post.getId());
        int deleted = deleteReaction(post.getId(), actor, type);
        if (deleted == SINGLE_ROW_AFFECTED) {
            decreaseCount(post.getId(), type, true);
            eventPublisher.publishEvent(new PostReactionEvent(post.getId(), type));
        }

        return response(post.getId(), type, false, deleted == SINGLE_ROW_AFFECTED);
    }

    public Set<ReactionType> findActiveTypes(
            String publicId, CustomUserDetails userDetails, String anonymousVoterId) {
        Post post = findPost(publicId);
        if (userDetails != null) {
            Member member = findMember(userDetails);
            return Set.copyOf(
                    reactionRepository.findTypesByPostIdAndMemberId(post.getId(), member.getId()));
        }
        if (anonymousVoterId == null || anonymousVoterId.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(
                reactionRepository.findTypesByPostIdAndAnonymousVoterId(
                        post.getId(), anonymousVoterId));
    }

    public List<ReactionCountMismatch> findCountMismatches(ReactionType type) {
        List<PostRepository.ReactionCountMismatchProjection> projections =
                type == ReactionType.LIKE
                        ? postRepository.findLikeCountMismatches(type.name())
                        : postRepository.findDislikeCountMismatches(type.name());
        return projections.stream()
                .map(
                        projection ->
                                new ReactionCountMismatch(
                                        projection.getPostId(),
                                        projection.getPostPublicId(),
                                        type,
                                        projection.getStoredCount(),
                                        projection.getActualCount()))
                .toList();
    }

    @Transactional
    public ReactionReconciliationResult reconcile(String publicId, ReactionType type) {
        Post post = findAvailablePost(publicId);
        PostRepository.ReactionCounts before = findCounts(post.getId());
        long beforeCount = countFor(before, type);

        if (type == ReactionType.LIKE) {
            postRepository.reconcileLikeCount(post.getId(), type.name());
        } else {
            postRepository.reconcileDislikeCount(post.getId(), type.name());
        }

        long afterCount = countFor(findCounts(post.getId()), type);
        boolean changed = beforeCount != afterCount;
        log.info(
                "반응 카운터 정합성 복구 - postPublicId: {}, type: {}, before: {}, after: {}, changed: {}",
                publicId,
                type,
                beforeCount,
                afterCount,
                changed);
        return new ReactionReconciliationResult(publicId, type, beforeCount, afterCount, changed);
    }

    private Post findAvailablePost(String publicId) {
        Post post = findPost(publicId);
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        return post;
    }

    private Post findPost(String publicId) {
        return postRepository
                .findByPublicId(publicId)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
    }

    private ReactionActor resolveActor(
            CustomUserDetails userDetails, String clientIp, String anonymousVoterId) {
        String resolvedIp = clientIp != null && !clientIp.isBlank() ? clientIp : DEFAULT_WRITER_IP;
        if (userDetails != null) {
            return ReactionActor.member(findMember(userDetails).getId(), resolvedIp);
        }
        if (anonymousVoterId == null || anonymousVoterId.isBlank()) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
        return ReactionActor.anonymous(anonymousVoterId, resolvedIp);
    }

    private Member findMember(CustomUserDetails userDetails) {
        return memberRepository
                .findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private int insertReaction(Long postId, ReactionActor actor, ReactionType type) {
        return actor.isMember()
                ? reactionRepository.insertMemberReaction(
                        postId, actor.memberId(), actor.writerIp(), type.name())
                : reactionRepository.insertAnonymousReaction(
                        postId, actor.anonymousVoterId(), actor.writerIp(), type.name());
    }

    private int deleteReaction(Long postId, ReactionActor actor, ReactionType type) {
        return actor.isMember()
                ? reactionRepository.deleteMemberReaction(postId, actor.memberId(), type)
                : reactionRepository.deleteAnonymousReaction(
                        postId, actor.anonymousVoterId(), type);
    }

    private void increaseCount(Long postId, ReactionType type) {
        int updated =
                type == ReactionType.LIKE
                        ? postRepository.increaseLikeCount(postId)
                        : postRepository.increaseDislikeCount(postId);
        validateSinglePostUpdated(updated);
    }

    private void decreaseCount(Long postId, ReactionType type) {
        decreaseCount(postId, type, false);
    }

    private void decreaseCount(Long postId, ReactionType type, boolean allowZeroRows) {
        int updated =
                type == ReactionType.LIKE
                        ? postRepository.decreaseLikeCount(postId)
                        : postRepository.decreaseDislikeCount(postId);
        if (allowZeroRows && updated == 0) {
            log.warn("추천 카운터 감소 대상 없음 - postId: {}, type: {}. 정합성 확인이 필요합니다.", postId, type);
            return;
        }
        validateSinglePostUpdated(updated);
    }

    private void validateSinglePostUpdated(int updated) {
        if (updated != SINGLE_ROW_AFFECTED) {
            throw new CustomAuthException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void lockReactionCounter(Long postId) {
        postRepository
                .findByIdForUpdate(postId)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
    }

    private ReactionResponse response(
            Long postId, ReactionType type, boolean active, boolean changed) {
        PostRepository.ReactionCounts counts = findCurrentCounts(postId);
        return new ReactionResponse(
                active,
                type,
                counts.getLikeCount(),
                counts.getDislikeCount(),
                changed,
                resolveMessage(type, active, changed));
    }

    private PostRepository.ReactionCounts findCounts(Long postId) {
        return postRepository
                .findReactionCountsById(postId)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
    }

    private PostRepository.ReactionCounts findCurrentCounts(Long postId) {
        return postRepository
                .findReactionCountsByIdForUpdate(postId)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
    }

    private long countFor(PostRepository.ReactionCounts counts, ReactionType type) {
        return type == ReactionType.LIKE ? counts.getLikeCount() : counts.getDislikeCount();
    }

    private String resolveMessage(ReactionType type, boolean active, boolean changed) {
        if (type == ReactionType.LIKE) {
            if (active) {
                return changed ? LIKE_APPLIED_MESSAGE : LIKE_ALREADY_APPLIED_MESSAGE;
            }
            return changed ? LIKE_REMOVED_MESSAGE : LIKE_ALREADY_REMOVED_MESSAGE;
        }
        if (active) {
            return changed ? DISLIKE_APPLIED_MESSAGE : DISLIKE_ALREADY_APPLIED_MESSAGE;
        }
        return changed ? DISLIKE_REMOVED_MESSAGE : DISLIKE_ALREADY_REMOVED_MESSAGE;
    }
}
