package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record PostListResponse(
        String publicId,
        String categoryName,
        String categoryCode,
        String title,
        String writerNickname,
        String writerProfileImageUrl,
        String writerPublicId,
        String thumbnailImageUrl,
        boolean hasImage,
        int viewCount,
        int commentCount,
        int likeCount,
        int dislikeCount,
        PostStatus status,
        boolean isDeleted,
        LocalDateTime createdAt,
        Boolean answered) {
    public static PostListResponse from(Post post) {
        return of(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.getTitle(),
                post.isAnonymous(),
                post.getMember(),
                post.getWriterIp(),
                post.isHasImage(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                post.getStatus(),
                post.isDeleted(),
                post.getCreatedAt());
    }

    /** 목록 조회가 본문(LOB) 없이 고른 컬럼만으로 만들 때 쓴다. {@link #from(Post)}과 같은 규칙이다. */
    public static PostListResponse of(
            String publicId,
            String categoryName,
            String categoryCode,
            String title,
            boolean isAnonymous,
            Member writer,
            String writerIp,
            boolean hasImage,
            int viewCount,
            int commentCount,
            int likeCount,
            int dislikeCount,
            PostStatus status,
            boolean isDeleted,
            LocalDateTime createdAt) {
        String writerNickname = WriterDisplayFormatter.format(isAnonymous, writer, writerIp);

        // 목록 썸네일은 화면에서 쓰지 않는다. 사진 컬렉션(LAZY)을 건드리면 글마다 쿼리가 하나씩 더 나가므로 읽지 않는다.
        String thumbnailUrl = null;

        return new PostListResponse(
                publicId,
                categoryName,
                categoryCode,
                isDeleted ? "[삭제된 게시글입니다]" : title,
                writerNickname,
                !isAnonymous && writer != null ? writer.getProfileImageUrl() : null,
                resolveWriterPublicId(isAnonymous, writer),
                thumbnailUrl,
                hasImage,
                viewCount,
                commentCount,
                likeCount,
                dislikeCount,
                // 금칙어 보류(FILTER_HOLD)는 작성자에게도 NORMAL로 보인다. 고른 컬럼으로 만드는 목록에서도 같은 규칙을 지킨다.
                status.publicView(),
                isDeleted,
                createdAt,
                null);
    }

    /** 쪽지를 받을 수 있는 작성자만 채운다. 익명 글에 넣으면 작성자가 드러난다. */
    private static String resolveWriterPublicId(boolean isAnonymous, Member writer) {
        return !isAnonymous && writer != null && writer.getStatus() == MemberStatus.ACTIVE
                ? writer.getPublicId()
                : null;
    }

    public PostListResponse withThumbnailImageUrl(String resolvedThumbnailImageUrl) {
        return new PostListResponse(
                publicId,
                categoryName,
                categoryCode,
                title,
                writerNickname,
                writerProfileImageUrl,
                writerPublicId,
                resolvedThumbnailImageUrl,
                hasImage,
                viewCount,
                commentCount,
                likeCount,
                dislikeCount,
                status,
                isDeleted,
                createdAt,
                answered);
    }

    /** 1:1 문의 목록에서만 채운다. 관리자 댓글이 하나라도 있으면 답변 완료다. */
    public PostListResponse withAnswered(Boolean resolvedAnswered) {
        return new PostListResponse(
                publicId,
                categoryName,
                categoryCode,
                title,
                writerNickname,
                writerProfileImageUrl,
                writerPublicId,
                thumbnailImageUrl,
                hasImage,
                viewCount,
                commentCount,
                likeCount,
                dislikeCount,
                status,
                isDeleted,
                createdAt,
                resolvedAnswered);
    }
}
