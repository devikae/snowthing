package com.ikae.snowthing.domain.comment.event;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.post.entity.Post;

/**
 * 댓글이 저장됐다는 사실. 커밋 뒤 새 트랜잭션에서 처리하는 리스너가 지연 로딩 없이 쓰도록 엔티티 대신 필요한 값만 담는다.
 *
 * <p>익명 댓글이면 {@code actorName}에 닉네임 대신 "익명"을 담아 알림으로 작성자가 드러나지 않게 한다.
 */
public record CommentCreatedEvent(
        Long commentId,
        Long postId,
        String postPublicId,
        String postCategoryCode,
        Long postAuthorId,
        Long parentAuthorId,
        Long actorId,
        boolean actorAdmin,
        boolean anonymous,
        String actorName,
        String preview) {

    public static final String ANONYMOUS_NAME = "익명";
    public static final int PREVIEW_LENGTH = 50;
    private static final int ACTOR_NAME_LENGTH = 50;

    public static CommentCreatedEvent from(Comment comment) {
        Post post = comment.getPost();
        Member actor = comment.getMember();
        Comment parent = comment.getParent();
        return new CommentCreatedEvent(
                comment.getId(),
                post.getId(),
                post.getPublicId(),
                post.getCategory().getCode(),
                memberId(post.getMember()),
                parent == null ? null : memberId(parent.getMember()),
                memberId(actor),
                actor != null && actor.getRole() == Role.ROLE_ADMIN,
                comment.isAnonymous(),
                actorName(comment.isAnonymous(), actor),
                preview(comment.getContent()));
    }

    private static Long memberId(Member member) {
        return member == null ? null : member.getId();
    }

    private static String actorName(boolean anonymous, Member actor) {
        if (anonymous || actor == null || actor.getNickname() == null) {
            return ANONYMOUS_NAME;
        }
        return cut(actor.getNickname(), ACTOR_NAME_LENGTH);
    }

    /** 줄바꿈과 연속 공백을 한 칸으로 줄이고 앞 50자만 남긴다. 이모지가 반으로 잘리지 않게 코드 포인트 기준으로 자른다. */
    static String preview(String content) {
        if (content == null) {
            return "";
        }
        return cut(content.strip().replaceAll("\\s+", " "), PREVIEW_LENGTH);
    }

    private static String cut(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }
}
