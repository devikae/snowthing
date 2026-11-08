package com.ikae.snowthing.domain.comment.controller;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.global.idempotency.IdempotencyTarget;
import com.ikae.snowthing.global.idempotency.IdempotentCreateHandler;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final ClientIpResolver clientIpResolver;
    private final ContentWriteRateLimiter contentWriteRateLimiter;
    private final IdempotentCreateHandler idempotentCreateHandler;

    @PostMapping("/posts/{publicId}/comments")
    public ResponseEntity<Object> createComment(
            @PathVariable String publicId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = IdempotentCreateHandler.HEADER, required = false)
                    String idempotencyKey,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return idempotentCreateHandler.create(
                IdempotencyTarget.COMMENT,
                idempotencyKey,
                userDetails,
                clientIp,
                List.of(publicId, request),
                () ->
                        contentWriteRateLimiter.check(
                                userDetails, clientIp, ContentWriteRateLimiter.Action.COMMENT),
                () -> commentService.createComment(publicId, request, userDetails, clientIp));
    }

    @GetMapping("/posts/{publicId}/comments")
    public ResponseEntity<PostCommentListResponse> getCommentsByPost(
            @PathVariable String publicId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PostCommentListResponse response =
                commentService.getCommentsByPost(publicId, cursor, size, userDetails);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<CommentReplyListResponse> getCommentReplies(
            @PathVariable Long commentId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                commentService.getCommentReplies(commentId, cursor, size, userDetails));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<CommentUpdateResponse> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        checkGuestPassword(userDetails, httpRequest);
        CommentUpdateResponse response =
                commentService.updateComment(commentId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long commentId,
            @RequestBody(required = false) CommentDeleteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        checkGuestPassword(userDetails, httpRequest);
        String password = (request != null) ? request.anonymousPassword() : null;
        commentService.deleteComment(commentId, password, userDetails);
        return ResponseEntity.ok(
                Map.of("message", "댓글이 정상적으로 삭제(Soft Delete) 처리되었습니다.", "commentId", commentId));
    }

    /** 비회원 댓글은 비밀번호로 수정, 삭제하므로 대입과 BCrypt 연산 남용을 IP 단위로 묶는다. */
    private void checkGuestPassword(CustomUserDetails userDetails, HttpServletRequest httpRequest) {
        if (userDetails == null) {
            contentWriteRateLimiter.check(
                    null,
                    clientIpResolver.resolve(httpRequest),
                    ContentWriteRateLimiter.Action.GUEST_PASSWORD);
        }
    }
}
