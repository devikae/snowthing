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
        boolean canEdit,
        boolean requiresPassword,
        boolean canDelete,
        boolean requiresDeletePassword,
        LocalDateTime createdAt) {

    private static final String ANONYMOUS_NAME = "ㅇㅇ";

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
                canDelete,
                requiresDeletePassword,
                createdAt);
    }

    public CommentResponse withReplyInfo(
            long replyCount, boolean hasMoreReplies, List<CommentResponse> replies) {
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
                canDelete,
                requiresDeletePassword,
                createdAt);
    }

    public CommentResponse withViewerPermissions(String viewerPublicId, boolean admin) {
        boolean guestAnonymous = ownerPublicId == null && isAnonymous;
        boolean owner = ownerPublicId != null && ownerPublicId.equals(viewerPublicId);

        boolean editable = !isDeleted && (owner || guestAnonymous);
        boolean editPasswordRequired = editable && guestAnonymous;

        boolean deletable = !isDeleted && (admin || owner || guestAnonymous);
        boolean deletePasswordRequired = deletable && !admin && guestAnonymous;

        List<CommentResponse> visibleReplies =
                previewReplies.stream()
                        .map(reply -> reply.withViewerPermissions(viewerPublicId, admin))
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
                editPasswordRequired,
                deletable,
                deletePasswordRequired,
                createdAt);
    }

    public CommentResponse withViewerPermissions(String viewerPublicId) {
        return withViewerPermissions(viewerPublicId, false);
    }

    public List<CommentResponse> children() {
        return previewReplies;
    }

    public String writerName() {
        if (!isAnonymous) {
            return (writer != null && writer.nickname() != null)
                    ? writer.nickname()
                    : ANONYMOUS_NAME;
        }
        if (writerIp == null || writerIp.isBlank()) {
            return ANONYMOUS_NAME;
        }

        String[] ip = writerIp.split("\\.");
        String shortIp = (ip.length >= 2) ? ip[0] + "." + ip[1] : writerIp;
        return ANONYMOUS_NAME + "(" + shortIp + ")";
    }
}
