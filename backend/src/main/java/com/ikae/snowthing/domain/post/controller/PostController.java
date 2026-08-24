package com.ikae.snowthing.domain.post.controller;

import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.AnonymousVoterCookieManager;
import com.ikae.snowthing.global.web.ClientIpResolver;
import com.ikae.snowthing.global.web.ViewCountCookieManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/posts", "/api/v1/posts"})
@RequiredArgsConstructor
public class PostController {

    private static final String DEFAULT_PAGE_SIZE_PARAM = "20";

    private final PostService postService;
    private final ClientIpResolver clientIpResolver;
    private final ViewCountCookieManager viewCountCookieManager;
    private final AnonymousVoterCookieManager anonymousVoterCookieManager;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
        @Valid @RequestBody PostCreateRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletRequest httpRequest
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        PostResponse response = postService.createPost(request, userDetails, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(
        @PathVariable String publicId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        boolean shouldIncreaseViewCount = viewCountCookieManager.markIfFirstView(publicId, request, response);
        PostDetailResponse postDetailResponse = postService.getPostDetail(publicId, userDetails, shouldIncreaseViewCount);
        return ResponseEntity.ok(postDetailResponse);
    }

    @GetMapping
    public ResponseEntity<com.ikae.snowthing.global.common.dto.CursorPageResponse<PostListResponse>> searchPosts(
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) Long resortId,
        @RequestParam(required = false) SearchType searchType,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) SortType sortType,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_PARAM) int size
    ) {
        PostSearchRequest request = new PostSearchRequest(
            categoryCode, resortId, searchType, keyword, sortType, page, cursor, size
        );

        // cursor 파라미터 존재 시 -> 모바일 Cursor 쿼리 처리
        if (org.springframework.util.StringUtils.hasText(cursor)) {
            return ResponseEntity.ok(postService.searchPostsByCursor(request));
        }

        // cursor 없으면 -> 웹 Offset 쿼리 처리
        return ResponseEntity.ok(postService.searchPostsByOffset(request));
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
    public ResponseEntity<com.ikae.snowthing.domain.post.dto.ReactionToggleResponse> reactToPost(
        @PathVariable String publicId,
        @Valid @RequestBody PostReactionRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String anonymousVoterId = userDetails == null
            ? anonymousVoterCookieManager.getOrCreate(httpRequest, httpResponse)
            : null;
        com.ikae.snowthing.domain.post.dto.ReactionToggleResponse response =
            postService.reactToPost(publicId, request.type(), userDetails, clientIp, anonymousVoterId);
        return ResponseEntity.ok(response);
    }
}
