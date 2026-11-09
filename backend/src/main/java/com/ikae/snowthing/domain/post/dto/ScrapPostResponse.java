package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostScrap;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

/** 내 스크랩 목록의 한 줄. 작성자 표시는 글 목록과 같은 규칙(익명 IP 마스킹, 탈퇴한 회원)을 따른다. */
public record ScrapPostResponse(
        String postPublicId,
        String categoryCode,
        String categoryName,
        String title,
        String writerNickname,
        int commentCount,
        int likeCount,
        LocalDateTime postCreatedAt,
        LocalDateTime scrappedAt) {

    public static ScrapPostResponse from(PostScrap scrap) {
        Post post = scrap.getPost();
        return new ScrapPostResponse(
                post.getPublicId(),
                post.getCategory().getCode(),
                post.getCategory().getName(),
                post.getTitle(),
                WriterDisplayFormatter.format(
                        post.isAnonymous(), post.getMember(), post.getWriterIp()),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getCreatedAt(),
                scrap.getCreatedAt());
    }
}
