package com.ikae.snowthing.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record CommentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long commentId,
        @JsonSerialize(using = ToStringSerializer.class) Long postId,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        WriterResponse writer,
        boolean isAnonymous,
        String writerIp,
        String content,
        boolean isDeleted,
        long replyCount,
        List<CommentResponse> previewReplies,
        boolean hasMoreReplies,
        @JsonIgnore String ownerPublicId,
        boolean canDelete,
        boolean requiresDeletePassword,
        LocalDateTime createdAt) {

    public CommentResponse {
        previewReplies = previewReplies == null ? List.of() : List.copyOf(previewReplies);
    }

    public record WriterResponse(String publicId, String nickname, String profileImageUrl) {}

    public static CommentResponse from(Comment comment) {
        Member member = comment.getMember();
        WriterResponse writer =
                !comment.isAnonymous() && member != null
                        ? new WriterResponse(
                                member.getPublicId(),
                                member.getNickname(),
                                member.getProfileImageUrl())
                        : null;
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                writer,
                comment.isAnonymous(),
                WriterDisplayFormatter.maskIp(comment.getWriterIp()),
                comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent(),
                comment.isDeleted(),
                0,
                List.of(),
                false,
                member == null ? null : member.getPublicId(),
                false,
                false,
                comment.getCreatedAt());
    }

    public CommentResponse withPreviewReplies(List<CommentResponse> replies) {
        return new CommentResponse(
                commentId,
                postId,
                parentId,
                writer,
                isAnonymous,
                writerIp,
                content,
                isDeleted,
                replyCount,
                replies,
                hasMoreReplies,
                ownerPublicId,
                canDelete,
                requiresDeletePassword,
                createdAt);
    }

    public CommentResponse withDeletePermissions(String viewerPublicId, boolean admin) {
        boolean guestAnonymous = ownerPublicId == null && isAnonymous;
        boolean owner = ownerPublicId != null && ownerPublicId.equals(viewerPublicId);
        boolean deletable = !isDeleted && (admin || owner || guestAnonymous);
        boolean passwordRequired = deletable && !admin && guestAnonymous;
        List<CommentResponse> visibleReplies =
                previewReplies.stream()
                        .map(reply -> reply.withDeletePermissions(viewerPublicId, admin))
                        .toList();
        return new CommentResponse(
                commentId,
                postId,
                parentId,
                writer,
                isAnonymous,
                writerIp,
                content,
                isDeleted,
                replyCount,
                visibleReplies,
                hasMoreReplies,
                ownerPublicId,
                deletable,
                passwordRequired,
                createdAt);
    }

    public List<CommentResponse> children() {
        return previewReplies;
    }

    public String writerName() {
        if (isAnonymous) {
            return "익명 (" + writerIp + ")";
        }
        return writer == null ? "알 수 없음" : writer.nickname();
    }
}
