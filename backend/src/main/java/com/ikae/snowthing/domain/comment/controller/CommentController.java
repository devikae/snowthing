package com.ikae.snowthing.domain.comment.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.comment.dto.*;
import com.ikae.snowthing.domain.comment.service.CommentService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/posts/{publicId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable String publicId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        CommentResponse response =
                commentService.createComment(publicId, request, userDetails, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/posts/{publicId}/comments")
    public ResponseEntity<PostCommentListResponse> getCommentsByPost(
            @PathVariable String publicId) {
        PostCommentListResponse response = commentService.getCommentsByPost(publicId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long commentId,
            @RequestBody(required = false) CommentDeleteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String password = (request != null) ? request.anonymousPassword() : null;
        commentService.deleteComment(commentId, password, userDetails);
        return ResponseEntity.ok(
                Map.of("message", "댓글이 정상적으로 삭제(Soft Delete) 처리되었습니다.", "commentId", commentId));
    }
}
