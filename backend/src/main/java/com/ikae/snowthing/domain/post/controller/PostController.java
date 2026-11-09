package com.ikae.snowthing.domain.post.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.service.PostScrapService;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.post.service.ReactionService;
import com.ikae.snowthing.global.idempotency.IdempotencyTarget;
import com.ikae.snowthing.global.idempotency.IdempotentCreateHandler;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.AnonymousVoterCookieManager;
import com.ikae.snowthing.global.web.ClientIpResolver;
import com.ikae.snowthing.global.web.ViewCountCookieManager;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/posts", "/api/v1/posts"})
@RequiredArgsConstructor
public class PostController {

    private static final String DEFAULT_PAGE_SIZE_PARAM = "20";
    private static final String DEFAULT_REACTION_TYPE_PARAM = "LIKE";

    private final PostService postService;
    private final ReactionService reactionService;
    private final PostScrapService postScrapService;
    private final ClientIpResolver clientIpResolver;
    private final ViewCountCookieManager viewCountCookieManager;
    private final AnonymousVoterCookieManager anonymousVoterCookieManager;
    private final ContentWriteRateLimiter contentWriteRateLimiter;
    private final IdempotentCreateHandler idempotentCreateHandler;

    @PostMapping
    public ResponseEntity<Object> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = IdempotentCreateHandler.HEADER, required = false)
                    String idempotencyKey,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return idempotentCreateHandler.create(
                IdempotencyTarget.POST,
                idempotencyKey,
                userDetails,
                clientIp,
                request,
                () ->
                        contentWriteRateLimiter.check(
                                userDetails, clientIp, ContentWriteRateLimiter.Action.POST),
                () -> postService.createPost(request, userDetails, clientIp));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<PostDetailResponse> getPostDetail(
            @PathVariable String publicId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {
        boolean shouldIncreaseViewCount =
                viewCountCookieManager.markIfFirstView(publicId, request, response);
        PostDetailResponse postDetailResponse =
                postService.getPostDetail(publicId, userDetails, shouldIncreaseViewCount);
        String anonymousVoterId = anonymousVoterCookieManager.find(request).orElse(null);
        return ResponseEntity.ok(
                postDetailResponse
                        .withActiveReactionTypes(
                                reactionService.findActiveTypes(
                                        publicId, userDetails, anonymousVoterId))
                        .withScrapped(postScrapService.isScrapped(publicId, userDetails)));
    }

    @GetMapping
    public ResponseEntity<com.ikae.snowthing.global.common.dto.CursorPageResponse<PostListResponse>>
            searchPostsByOffset(
                    @RequestParam(required = false) String categoryCode,
                    @RequestParam(required = false) Long resortId,
                    @RequestParam(required = false) SearchType searchType,
                    @RequestParam(required = false) String keyword,
                    @RequestParam(required = false) PostViewType viewType,
                    @RequestParam(required = false, defaultValue = "1") Integer page,
                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_PARAM) int size,
                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        PostSearchRequest request =
                new PostSearchRequest(
                        categoryCode, resortId, searchType, keyword, viewType, page, null, size);
        return ResponseEntity.ok(postService.searchPostsByOffset(request, userDetails));
    }

    @GetMapping("/scroll")
    public ResponseEntity<com.ikae.snowthing.global.common.dto.CursorPageResponse<PostListResponse>>
            searchPostsByCursor(
                    @RequestParam(required = false) String categoryCode,
                    @RequestParam(required = false) Long resortId,
                    @RequestParam(required = false) SearchType searchType,
                    @RequestParam(required = false) String keyword,
                    @RequestParam(required = false) PostViewType viewType,
                    @RequestParam(required = false) String cursor,
                    @RequestParam(defaultValue = DEFAULT_PAGE_SIZE_PARAM) int size,
                    @AuthenticationPrincipal CustomUserDetails userDetails) {
        PostSearchRequest request =
                new PostSearchRequest(
                        categoryCode, resortId, searchType, keyword, viewType, null, cursor, size);
        return ResponseEntity.ok(postService.searchPostsByCursor(request, userDetails));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable String publicId,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        checkGuestPassword(userDetails, httpRequest);
        PostResponse response = postService.updatePost(publicId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Map<String, Object>> deletePost(
            @PathVariable String publicId,
            @RequestBody(required = false) PostDeleteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        checkGuestPassword(userDetails, httpRequest);
        String password = request != null ? request.anonymousPassword() : null;
        postService.deletePost(publicId, password, userDetails);
        return ResponseEntity.ok(
                Map.of("message", "게시글이 정상적으로 삭제(Soft Delete) 처리되었습니다.", "publicId", publicId));
    }

    @PutMapping("/{publicId}/reaction")
    public ResponseEntity<ReactionResponse> applyReaction(
            @PathVariable String publicId,
            @RequestParam(defaultValue = DEFAULT_REACTION_TYPE_PARAM) ReactionType type,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        if (userDetails == null) {
            contentWriteRateLimiter.checkGuestReaction(clientIp, publicId);
        }
        String anonymousVoterId =
                userDetails == null
                        ? anonymousVoterCookieManager.getOrCreate(httpRequest, httpResponse)
                        : null;
        return ResponseEntity.ok(
                reactionService.apply(publicId, type, userDetails, clientIp, anonymousVoterId));
    }

    @DeleteMapping("/{publicId}/reaction")
    public ResponseEntity<ReactionResponse> removeReaction(
            @PathVariable String publicId,
            @RequestParam(defaultValue = DEFAULT_REACTION_TYPE_PARAM) ReactionType type,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        String anonymousVoterId =
                userDetails == null
                        ? anonymousVoterCookieManager.getOrCreate(httpRequest, httpResponse)
                        : null;
        return ResponseEntity.ok(
                reactionService.remove(publicId, type, userDetails, clientIp, anonymousVoterId));
    }

    /** 비회원 글은 비밀번호로 수정, 삭제하므로 대입과 BCrypt 연산 남용을 IP 단위로 묶는다. */
    private void checkGuestPassword(CustomUserDetails userDetails, HttpServletRequest httpRequest) {
        if (userDetails == null) {
            contentWriteRateLimiter.check(
                    null,
                    clientIpResolver.resolve(httpRequest),
                    ContentWriteRateLimiter.Action.GUEST_PASSWORD);
        }
    }
}
