package com.ikae.snowthing.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record CommentResponse(
        Long commentId,
        Long postId,
        Long parentId,
        WriterResponse writer,
        boolean isAnonymous,
        String writerIp,
        String content,
        boolean isDeleted,
        long replyCount,
        List<CommentResponse> previewReplies,
        boolean hasMoreReplies,
        @JsonIgnore String ownerPublicId,
        boolean canEdit,
        boolean requiresPassword,
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
                comment.isAnonymous() ? WriterDisplayFormatter.maskIp(comment.getWriterIp()) : null,
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
                canEdit,
                requiresPassword,
                createdAt);
    }

    public CommentResponse withViewerPermissions(String viewerPublicId) {
        boolean editable =
                !isDeleted
                        && (ownerPublicId == null
                                ? isAnonymous
                                : ownerPublicId.equals(viewerPublicId));
        boolean passwordRequired = editable && ownerPublicId == null && isAnonymous;
        List<CommentResponse> visibleReplies =
                previewReplies.stream()
                        .map(reply -> reply.withViewerPermissions(viewerPublicId))
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
                editable,
                passwordRequired,
                createdAt);
    }

    public List<CommentResponse> children() {
        return previewReplies;
    }

    public String writerName() {
        if (isAnonymous) {
            return writerIp != null ? "익명 (" + writerIp + ")" : "익명";
        }
        return writer == null ? "알 수 없음" : writer.nickname();
    }
}
