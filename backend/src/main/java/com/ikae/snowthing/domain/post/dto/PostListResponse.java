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
        String writerNickname =
                WriterDisplayFormatter.format(
                        post.isAnonymous(), post.getMember(), post.getWriterIp());

        String thumbnailUrl =
                post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl();

        return new PostListResponse(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.isDeleted() ? "[삭제된 게시글입니다]" : post.getTitle(),
                writerNickname,
                !post.isAnonymous() && post.getMember() != null
                        ? post.getMember().getProfileImageUrl()
                        : null,
                resolveWriterPublicId(post),
                thumbnailUrl,
                post.isHasImage(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                post.getStatus(),
                post.isDeleted(),
                post.getCreatedAt(),
                null);
    }

    /** 쪽지를 받을 수 있는 작성자만 채운다. 익명 글에 넣으면 작성자가 드러난다. */
    private static String resolveWriterPublicId(Post post) {
        Member writer = post.getMember();
        return !post.isAnonymous() && writer != null && writer.getStatus() == MemberStatus.ACTIVE
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
