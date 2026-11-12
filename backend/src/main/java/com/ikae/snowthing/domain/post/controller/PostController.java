package com.ikae.snowthing.domain.post.controller;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.ReactionType;
import com.ikae.snowthing.domain.post.service.PostScrapService;
import com.ikae.snowthing.domain.post.service.PostSearchRateLimiter;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.post.service.ReactionService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
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
    private final PostSearchRateLimiter postSearchRateLimiter;

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
                    @AuthenticationPrincipal CustomUserDetails userDetails,
                    HttpServletRequest httpRequest) {
        checkKeywordSearch(keyword, httpRequest);
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
                    @AuthenticationPrincipal CustomUserDetails userDetails,
                    HttpServletRequest httpRequest) {
        checkKeywordSearch(keyword, httpRequest);
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
        checkGuestPassword(userDetails, request.anonymousPassword(), httpRequest);
        PostResponse response = postService.updatePost(publicId, request, userDetails);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Map<String, Object>> deletePost(
            @PathVariable String publicId,
            @RequestBody(required = false) PostDeleteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        String password = request != null ? request.anonymousPassword() : null;
        checkGuestPassword(userDetails, password, httpRequest);
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
        if (userDetails == null) {
            // 취소는 한 글당 횟수를 쓰지 않게 IP 단위 상한만 추천과 함께 쓴다. 추천, 취소를 번갈아 연타해 DB 쓰기를 늘리지 못하게 한다.
            contentWriteRateLimiter.check(
                    null, clientIp, ContentWriteRateLimiter.Action.GUEST_REACTION);
        }
        String anonymousVoterId =
                userDetails == null
                        ? anonymousVoterCookieManager.getOrCreate(httpRequest, httpResponse)
                        : null;
        return ResponseEntity.ok(
                reactionService.remove(publicId, type, userDetails, clientIp, anonymousVoterId));
    }

    /**
     * 검색어가 있으면 글 본문까지 LIKE로 훑어 글이 늘수록 비싸진다. 한 글자 검색은 거의 모든 글에 걸려 의미가 없으므로 막고, 같은 IP의 연속 검색을 묶는다.
     * 검색어 없는 목록 조회는 인덱스를 타므로 제한하지 않는다.
     */
    private void checkKeywordSearch(String keyword, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(keyword)) return;
        int length = keyword.strip().codePointCount(0, keyword.strip().length());
        if (length < PostSearchRateLimiter.MIN_KEYWORD_LENGTH
                || length > PostSearchRateLimiter.MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.POST_SEARCH_KEYWORD_INVALID);
        }
        postSearchRateLimiter.check(clientIpResolver.resolve(httpRequest));
    }

    /**
     * 비회원 글은 비밀번호로 수정, 삭제하므로 대입과 BCrypt 연산 남용을 IP 단위로 묶는다. 로그인 회원도 남의 익명 글에 비밀번호를 보내 확인받을 수 있으므로,
     * 비밀번호가 실려 오면 로그인 여부와 관계없이 같은 IP 제한을 건다.
     */
    private void checkGuestPassword(
            CustomUserDetails userDetails,
            String anonymousPassword,
            HttpServletRequest httpRequest) {
        if (userDetails == null || StringUtils.hasText(anonymousPassword)) {
            contentWriteRateLimiter.check(
                    null,
                    clientIpResolver.resolve(httpRequest),
                    ContentWriteRateLimiter.Action.GUEST_PASSWORD);
        }
    }
}
