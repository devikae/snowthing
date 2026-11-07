package com.ikae.snowthing.domain.contentreport.service;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;

/** 조치 대상. 댓글이면 {@code comment}가 있고 {@code post}는 그 댓글의 글이다. */
record ModerationTarget(Post post, Comment comment) {

    boolean isDeleted() {
        return comment != null
                ? comment.isDeleted() && !comment.isModerationHeld()
                : post.isDeleted();
    }

    boolean isHeld() {
        return comment != null
                ? comment.isModerationHeld()
                : !post.isDeleted() && post.getStatus() == PostStatus.TEMPORARY_HOLD;
    }

    /** 일반 사용자에게 보이는 상태. 관리자가 숨긴 글을 임시조치하면 복구 때 공개되므로 구분한다. */
    boolean isPublic() {
        if (comment != null) {
            return !comment.isDeleted() && post.getStatus() == PostStatus.NORMAL;
        }
        return !post.isDeleted() && post.getStatus() == PostStatus.NORMAL;
    }

    Member author() {
        return comment != null ? comment.getMember() : post.getMember();
    }

    Long commentId() {
        return comment != null ? comment.getId() : null;
    }

    String label() {
        return comment != null ? "댓글" : "게시글";
    }
}
