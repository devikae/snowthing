package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.aimember.AiReactionPolicy.CommentView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;

class AiReactionPolicyTest {

    private static final long AI = 1L;
    private static final long USER = 100L;
    private static final LocalDateTime POSTED = LocalDateTime.of(2026, 12, 20, 12, 0);
    private static final LocalDateTime LATER = POSTED.plusHours(6);

    @Test
    @DisplayName("작성자 자신은 자기 글에 첫 댓글을 달지 않는다")
    void authorDoesNotCommentOwnPost() {
        PostView post = post(10L, AI);

        Optional<Reaction> reaction = next(List.of(post), Map.of(), List.of(AI), Set.of());

        assertThat(reaction).isEmpty();
    }

    @Test
    @DisplayName("실제 사용자 스레드에서 AI 답글은 2개까지만 만들고 3번째는 만들지 않는다")
    void stopsAfterTwoRepliesInUserThread() {
        PostView post = post(10L, USER);
        List<CommentView> thread = new ArrayList<>();
        thread.add(comment(1L, AI, null, POSTED.plusMinutes(30)));
        thread.add(comment(2L, USER, 1L, POSTED.plusMinutes(40)));
        thread.add(comment(3L, AI, 1L, POSTED.plusMinutes(60)));
        thread.add(comment(4L, USER, 1L, POSTED.plusMinutes(70)));
        thread.add(comment(5L, AI, 1L, POSTED.plusMinutes(90)));
        thread.add(comment(6L, USER, 1L, POSTED.plusMinutes(100)));

        Optional<Reaction> reaction =
                next(List.of(post), Map.of(10L, thread), List.of(AI), Set.of());

        assertThat(reaction).isEmpty();
    }

    @Test
    @DisplayName("실패한 시도는 다시 고르지 않는다")
    void skipsAttemptedReaction() {
        PostView post = post(10L, USER);
        Reaction first =
                firstDue(post).orElseThrow(() -> new AssertionError("no AI chose to comment"));

        Optional<Reaction> again =
                next(List.of(post), Map.of(), List.of(first.memberId()), Set.of(first.key()));

        assertThat(again).isEmpty();
    }

    @Test
    @DisplayName("하루 댓글 상한에 닿은 AI는 고르지 않는다")
    void respectsDailyCommentCap() {
        PostView post = post(10L, USER);
        Reaction first = firstDue(post).orElseThrow();

        Optional<Reaction> capped =
                AiReactionPolicy.next(
                        List.of(post),
                        Map.of(),
                        List.of(first.memberId()),
                        Map.of(first.memberId(), AiReactionPolicy.MAX_COMMENTS_PER_DAY),
                        Set.of(),
                        LATER);

        assertThat(capped).isEmpty();
    }

    @Test
    @DisplayName("실제 사용자가 AI 댓글에 답하면 AI가 같은 스레드에 답글을 단다")
    void repliesToUserInThread() {
        PostView post = post(10L, USER);
        boolean replied = false;
        for (long commentId = 1; commentId <= 40 && !replied; commentId++) {
            List<CommentView> thread =
                    List.of(
                            comment(commentId * 10, AI, null, POSTED.plusMinutes(30)),
                            comment(
                                    commentId * 10 + 1,
                                    USER,
                                    commentId * 10,
                                    POSTED.plusMinutes(40)));
            Optional<Reaction> reaction =
                    next(List.of(post), Map.of(10L, thread), List.of(AI), Set.of());
            if (reaction.isPresent()) {
                assertThat(reaction.get().parentCommentId()).isEqualTo(commentId * 10);
                replied = true;
            }
        }
        assertThat(replied).isTrue();
    }

    private static Optional<Reaction> firstDue(PostView post) {
        for (long memberId = 1; memberId <= 50; memberId++) {
            Optional<Reaction> reaction =
                    next(List.of(post), Map.of(), List.of(memberId), Set.of());
            if (reaction.isPresent()) {
                return reaction;
            }
        }
        return Optional.empty();
    }

    private static Optional<Reaction> next(
            List<PostView> posts,
            Map<Long, List<CommentView>> comments,
            List<Long> members,
            Set<String> attempted) {
        return AiReactionPolicy.next(posts, comments, members, Map.of(), attempted, LATER);
    }

    private static PostView post(long postId, Long memberId) {
        return new PostView(postId, "public-" + postId, memberId, "FREE", "제목", "본문", POSTED);
    }

    private static CommentView comment(
            long commentId, Long memberId, Long parentId, LocalDateTime createdAt) {
        return new CommentView(commentId, 10L, memberId, parentId, "댓글", false, createdAt);
    }
}
