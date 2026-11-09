package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostImage;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record PostDetailResponse(
        String publicId,
        String categoryName,
        String categoryCode,
        String title,
        String content,
        PostStatus status,
        boolean isAnonymous,
        boolean isAuthor,
        boolean requiresPassword,
        int viewCount,
        int commentCount,
        int likeCount,
        int dislikeCount,
        WriterInfo writer,
        List<String> images,
        Set<ReactionType> activeReactionTypes,
        boolean scrapped,
        LocalDateTime createdAt) {

    public PostDetailResponse {
        images = List.copyOf(images);
        activeReactionTypes = Set.copyOf(activeReactionTypes);
    }

    private static WriterInfo memberWriter(Member member) {
        if (member == null) {
            return WriterInfo.anonymous("알 수 없음");
        }
        if (member.isWithdrawn()) {
            return WriterInfo.member(null, Member.WITHDRAWN_NICKNAME, null);
        }
        return WriterInfo.member(
                member.getPublicId(), member.getNickname(), member.getProfileImageUrl());
    }

    public record WriterInfo(String publicId, String nickname, String profileImageUrl) {
        public static WriterInfo anonymous(String maskedIp) {
            return new WriterInfo(null, maskedIp, null);
        }

        public static WriterInfo member(String publicId, String nickname, String profileImageUrl) {
            return new WriterInfo(publicId, nickname, profileImageUrl);
        }
    }

    public static PostDetailResponse from(
            Post post, int viewCount, List<String> imageUrls, String viewerPublicId) {
        boolean isAuthor =
                viewerPublicId != null
                        && post.getMember() != null
                        && viewerPublicId.equals(post.getMember().getPublicId());
        WriterInfo writerInfo =
                post.isAnonymous()
                        ? WriterInfo.anonymous(
                                WriterDisplayFormatter.formatAnonymous(post.getWriterIp()))
                        : memberWriter(post.getMember());

        return new PostDetailResponse(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.getTitle(),
                post.getContent(),
                post.getStatus(),
                post.isAnonymous(),
                isAuthor,
                post.isAnonymous() && !isAuthor && post.getAnonymousPassword() != null,
                viewCount,
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                writerInfo,
                List.copyOf(imageUrls),
                Set.of(),
                false,
                post.getCreatedAt());
    }

    public PostDetailResponse withActiveReactionTypes(Set<ReactionType> reactionTypes) {
        return new PostDetailResponse(
                publicId,
                categoryName,
                categoryCode,
                title,
                content,
                status,
                isAnonymous,
                isAuthor,
                requiresPassword,
                viewCount,
                commentCount,
                likeCount,
                dislikeCount,
                writer,
                images,
                reactionTypes,
                scrapped,
                createdAt);
    }

    /** 로그인한 회원이 이 글을 스크랩했는지. 추천 여부처럼 컨트롤러가 붙인다. */
    public PostDetailResponse withScrapped(boolean isScrapped) {
        return new PostDetailResponse(
                publicId,
                categoryName,
                categoryCode,
                title,
                content,
                status,
                isAnonymous,
                isAuthor,
                requiresPassword,
                viewCount,
                commentCount,
                likeCount,
                dislikeCount,
                writer,
                images,
                activeReactionTypes,
                isScrapped,
                createdAt);
    }

    public static PostDetailResponse from(Post post, int viewCount) {
        List<String> imageUrls = post.getImages().stream().map(PostImage::getImageUrl).toList();
        return from(post, viewCount, imageUrls, null);
    }

    public static PostDetailResponse from(Post post) {
        return from(post, post.getViewCount());
    }
}
