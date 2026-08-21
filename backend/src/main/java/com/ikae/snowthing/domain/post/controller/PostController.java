package com.ikae.snowthing.domain.post.controller;

import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
        @Valid @RequestBody PostCreateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletRequest httpRequest
    ) {
        String clientIp = getClientIp(httpRequest);
        PostResponse response = postService.createPost(request, userDetails, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(
        @PathVariable String publicId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PostDetailResponse response = postService.getPostDetail(publicId, userDetails);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<PostListResponse>> getPostList(
        @RequestParam(required = false) String categoryCode,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        Page<PostListResponse> response = postService.getPostList(categoryCode, page, size);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<PostResponse> updatePost(
        @PathVariable String publicId,
        @Valid @RequestBody PostUpdateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        PostResponse response = postService.updatePost(publicId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Map<String, Object>> deletePost(
        @PathVariable String publicId,
        @RequestParam(required = false) String anonymousPassword,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        postService.deletePost(publicId, anonymousPassword, userDetails);
        return ResponseEntity.ok(Map.of(
            "message", "게시글이 정상적으로 삭제(Soft Delete) 처리되었습니다.",
            "publicId", publicId
        ));
    }

    @PostMapping("/{publicId}/reactions")
    public ResponseEntity<Map<String, String>> reactToPost(
        @PathVariable String publicId,
        @Valid @RequestBody PostReactionRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        postService.reactToPost(publicId, request.type(), userDetails);
        return ResponseEntity.ok(Map.of(
            "message", "투표가 성공적으로 수신되었습니다.",
            "type", request.type().name()
        ));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "127.0.0.1";
    }
}
