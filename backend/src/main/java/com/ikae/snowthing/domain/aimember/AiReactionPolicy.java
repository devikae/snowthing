package com.ikae.snowthing.domain.aimember;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * 댓글을 언제 누가 달지 정한다. 글·댓글과 회원 ID로 시드를 정하므로 같은 대상에 대한 판단은 매 분 같다. "이미 달았는지"는 DB의 댓글로 판단한다.
 *
 * <ul>
 *   <li>새 글: 작성자가 아닌 AI 멤버가 확률적으로, 20분~4시간 뒤 댓글을 단다.
 *   <li>스레드: 글 작성자이거나 그 스레드에 댓글을 단 AI 멤버는 마지막 댓글이 남의 것이면 10분~2시간 뒤 답글을 단다. 스레드마다 답글은 최대 2개라서 실제
 *       사용자와의 대화도, AI끼리의 키배도 거기서 멈춘다.
 * </ul>
 */
public final class AiReactionPolicy {

    static final int MAX_REPLIES_PER_THREAD = 2;
    static final int MAX_COMMENTS_PER_DAY = 8;
    static final double AI_POST_CHANCE = 0.35;
    static final double USER_POST_CHANCE = 0.5;
    static final double REPLY_CHANCE = 0.5;

    public record PostView(
            long postId,
            String publicId,
            Long memberId,
            String board,
            String title,
            String content,
            LocalDateTime createdAt) {}

    public record CommentView(
            long commentId,
            long postId,
            Long memberId,
            Long parentId,
            String content,
            boolean anonymous,
            LocalDateTime createdAt) {

        long rootId() {
            return parentId != null ? parentId : commentId;
        }
    }

    /** parentCommentId가 null이면 글에 바로 다는 댓글 */
    public record Reaction(long memberId, PostView post, Long parentCommentId) {

        /** 실패한 시도를 다시 고르지 않도록 실행 쪽이 기억하는 키 */
        public String key() {
            return memberId + ":" + post.postId() + ":" + parentCommentId;
        }
    }

    private AiReactionPolicy() {}

    public static Optional<Reaction> next(
            List<PostView> posts,
            Map<Long, List<CommentView>> commentsByPost,
            List<Long> aiMemberIds,
            Map<Long, Integer> commentsToday,
            Set<String> attempted,
            LocalDateTime now) {
        for (PostView post : posts) {
            List<CommentView> comments = commentsByPost.getOrDefault(post.postId(), List.of());
            for (Long memberId : aiMemberIds) {
                if (commentsToday.getOrDefault(memberId, 0) >= MAX_COMMENTS_PER_DAY) {
                    continue;
                }
                if (dueTopLevel(post, comments, memberId, aiMemberIds, now)) {
                    Reaction reaction = new Reaction(memberId, post, null);
                    if (!attempted.contains(reaction.key())) {
                        return Optional.of(reaction);
                    }
                }
                Optional<Long> root = dueReplyRoot(post, comments, memberId, now);
                if (root.isPresent()) {
                    Reaction reaction = new Reaction(memberId, post, root.get());
                    if (!attempted.contains(reaction.key())) {
                        return Optional.of(reaction);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean dueTopLevel(
            PostView post,
            List<CommentView> comments,
            long memberId,
            List<Long> aiMemberIds,
            LocalDateTime now) {
        if (Long.valueOf(memberId).equals(post.memberId())
                || comments.stream().anyMatch(comment -> wroteBy(comment, memberId))) {
            return false;
        }
        SplittableRandom random = new SplittableRandom(post.postId() * 31 + memberId);
        double chance =
                post.memberId() != null && Set.copyOf(aiMemberIds).contains(post.memberId())
                        ? AI_POST_CHANCE
                        : USER_POST_CHANCE;
        if (random.nextDouble() >= chance) {
            return false;
        }
        return !now.isBefore(post.createdAt().plusMinutes(20 + random.nextInt(221)));
    }

    private static Optional<Long> dueReplyRoot(
            PostView post, List<CommentView> comments, long memberId, LocalDateTime now) {
        boolean postAuthor = Long.valueOf(memberId).equals(post.memberId());
        for (List<CommentView> thread : threads(comments).values()) {
            CommentView last = thread.get(thread.size() - 1);
            if (wroteBy(last, memberId)) {
                continue;
            }
            boolean involved = postAuthor || thread.stream().anyMatch(c -> wroteBy(c, memberId));
            long replies =
                    thread.stream()
                            .filter(comment -> comment.parentId() != null)
                            .filter(comment -> wroteBy(comment, memberId))
                            .count();
            if (!involved || replies >= MAX_REPLIES_PER_THREAD) {
                continue;
            }
            SplittableRandom random = new SplittableRandom(last.commentId() * 31 + memberId);
            if (random.nextDouble() < REPLY_CHANCE
                    && !now.isBefore(last.createdAt().plusMinutes(10 + random.nextInt(111)))) {
                return Optional.of(thread.get(0).rootId());
            }
        }
        return Optional.empty();
    }

    static Map<Long, List<CommentView>> threads(List<CommentView> comments) {
        Map<Long, List<CommentView>> threads = new LinkedHashMap<>();
        for (CommentView comment : comments) {
            threads.computeIfAbsent(comment.rootId(), ignored -> new ArrayList<>()).add(comment);
        }
        return threads;
    }

    private static boolean wroteBy(CommentView comment, long memberId) {
        return Long.valueOf(memberId).equals(comment.memberId());
    }
}
