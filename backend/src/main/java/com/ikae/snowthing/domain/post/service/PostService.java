package com.ikae.snowthing.domain.post.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService;
import com.ikae.snowthing.domain.image.service.ImageUrlResolver;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.moderation.service.ModerationEnqueuer;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.util.MarkdownBodySanitizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final String DEFAULT_WRITER_IP = "127.0.0.1";
    private static final int FIRST_IMAGE_SORT_ORDER = 1;
    private static final int MAX_OFFSET_PAGE = 100;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_ID = "id";

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ImageUrlResolver imageUrlResolver;
    private final com.ikae.snowthing.domain.image.service.PostImageLifecycleService imageLifecycle;
    private final com.ikae.snowthing.domain.search.service.SearchOutboxWriter searchOutbox;
    private final ChatIpBanService chatIpBanService;
    private final ContentFilterService contentFilter;
    private final ModerationEnqueuer moderation;

    @Transactional
    public PostResponse createPost(
            PostCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        if (!isAdmin(userDetails)) {
            chatIpBanService.requireWritable(clientIp);
        }
        PostCategory category =
                categoryRepository
                        .findByCode(request.categoryCode())
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));
        rejectOwnScreenCategory(category);
        requireOpenBoard(category);
        requireAdminBoardWriter(category, userDetails);
        if (PostCategoryCodes.isInquiry(category.getCode())
                && !InquiryAccess.isSessionUser(userDetails)) {
            throw new CustomAuthException(ErrorCode.INQUIRY_LOGIN_REQUIRED);
        }

        // 비회원은 누구나 쓰는 게시판(익명게시판 등)에만 쓴다. isAnonymous=true로 다른 게시판에 쓰는 API 경로를 막는다.
        if (userDetails == null && !category.allowsGuest()) {
            throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
        }

        if (category.forbidsAnonymous() && request.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        Member member = null;
        String encodedPassword = null;

        boolean isAnon = request.isAnonymous() || category.isAlwaysAnonymous();

        if (isAnon) {
            if (userDetails != null) {
                member = memberRepository.findByPublicId(userDetails.getPublicId()).orElse(null);
            }
            if (member == null
                    && (request.anonymousPassword() == null
                            || request.anonymousPassword().isBlank())) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
            if (request.anonymousPassword() != null && !request.anonymousPassword().isBlank()) {
                encodedPassword = passwordEncoder.encode(request.anonymousPassword());
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.LOGIN_REQUIRED);
            }
            member =
                    memberRepository
                            .findByPublicId(userDetails.getPublicId())
                            .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
        }

        // 본문은 화면에서 HTML로 그려진다. 저장 전에 위험한 태그와 주소를 글자로 바꾼다.
        String content = MarkdownBodySanitizer.sanitize(request.content());
        // 금칙어가 걸리면 저장과 응답은 평소와 같고 상태만 FILTER_HOLD로 둔다 (IKA-96).
        Optional<String> matched = contentFilter.inspect(userDetails, request.title(), content);
        Post post =
                Post.builder()
                        .member(member)
                        .category(category)
                        .title(request.title())
                        .content(content)
                        .writerIp(resolveWriterIp(clientIp))
                        .isAnonymous(isAnon)
                        .anonymousPassword(encodedPassword)
                        .hasImage(false)
                        .status(matched.isPresent() ? PostStatus.FILTER_HOLD : null)
                        .build();
        imageLifecycle.rejectNewInlineImages(content, null);
        imageLifecycle.protectAll(
                request.imageUrls(),
                content,
                userDetails == null ? null : userDetails.getUsername());
        post.replaceImages(toPostImages(request.imageUrls()));
        post.replaceContentImageKeys(imageLifecycle.collectKeys(List.of(), content));

        Post saved = postRepository.save(post);
        matched.ifPresent(word -> contentFilter.recordPostHold(saved, word));
        searchOutbox.postChanged(saved);
        moderation.postWritten(saved);
        return PostResponse.from(saved);
    }

    /**
     * 상세 아래 이전글, 다음글. 게시판 목록에 나오는 글만 대상이다. 1:1 문의, 중고장터, 카풀은 목록 화면이 따로라 빈 값을 준다. 기준 글이 지워졌거나 숨김이어도
     * 빈 값이라 글이 있는지 드러나지 않는다.
     */
    public PostAdjacentResponse getAdjacentPosts(String publicId) {
        Post base = postRepository.findWithMemberAndCategoryByPublicId(publicId).orElse(null);
        if (base == null
                || base.isDeleted()
                || base.getStatus() != PostStatus.NORMAL
                || PostCategoryCodes.HIDDEN_FROM_BOARD_LIST.contains(
                        base.getCategory().getCode())) {
            return PostAdjacentResponse.NONE;
        }
        return new PostAdjacentResponse(
                postRepository
                        .findAdjacentInCategory(base, true)
                        .map(this::adjacentItem)
                        .orElse(null),
                postRepository
                        .findAdjacentInCategory(base, false)
                        .map(this::adjacentItem)
                        .orElse(null));
    }

    private PostAdjacentResponse.Item adjacentItem(Post post) {
        return new PostAdjacentResponse.Item(post.getPublicId(), post.getTitle());
    }

    @Transactional
    public PostDetailResponse getPostDetail(String publicId, CustomUserDetails userDetails) {
        return getPostDetail(publicId, userDetails, true);
    }

    @Transactional
    public PostDetailResponse getPostDetail(
            String publicId, CustomUserDetails userDetails, boolean shouldIncreaseViewCount) {
        return getPostDetail(publicId, userDetails, shouldIncreaseViewCount, null);
    }

    /**
     * @param viewerIp 금칙어 보류 글의 비회원 작성자를 알아볼 때 쓴다 (IKA-96)
     */
    @Transactional
    public PostDetailResponse getPostDetail(
            String publicId,
            CustomUserDetails userDetails,
            boolean shouldIncreaseViewCount,
            String viewerIp) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        rejectMarketPost(post);
        InquiryAccess.requireReadable(post, userDetails);

        if (post.getStatus() != PostStatus.NORMAL
                && !contentFilter.canSeeHeldPost(post, userDetails, viewerIp)) {
            if (!isAdmin(userDetails)) {
                throw new CustomAuthException(
                        post.getStatus() == PostStatus.TEMPORARY_HOLD
                                ? ErrorCode.CONTENT_TEMPORARILY_HELD
                                : ErrorCode.POST_NOT_FOUND);
            }
        }

        List<String> imageUrls =
                post.getImages().stream()
                        .map(PostImage::getImageUrl)
                        .map(imageUrlResolver::toPublicUrl)
                        .toList();
        int viewCount = post.getViewCount();
        if (shouldIncreaseViewCount) {
            postRepository.increaseViewCount(post.getId());
            viewCount++;
        }
        return PostDetailResponse.from(
                post, viewCount, imageUrls, userDetails == null ? null : userDetails.getPublicId());
    }

    public Page<PostListResponse> getPostList(String categoryCode, int page, int size) {
        if (page > MAX_OFFSET_PAGE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_LIMIT);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, SORT_CREATED_AT)
                                .and(Sort.by(Sort.Direction.DESC, SORT_ID)));

        Page<Post> posts;
        if (categoryCode != null && !categoryCode.isBlank()) {
            posts =
                    postRepository.findByCategoryCodeWithMemberAndCategory(
                            categoryCode, PostCategoryCodes.HIDDEN_FROM_PUBLIC, pageable);
        } else {
            posts =
                    postRepository.findAllWithMemberAndCategory(
                            PostCategoryCodes.HIDDEN_FROM_BOARD_LIST, pageable);
        }

        return posts.map(this::toPostListResponse);
    }

    public CursorPageResponse<PostListResponse> searchPostsByOffset(PostSearchRequest request) {
        return searchPostsByOffset(request, null);
    }

    public CursorPageResponse<PostListResponse> searchPostsByOffset(
            PostSearchRequest request, CustomUserDetails userDetails) {
        return searchPostsByOffset(request, userDetails, null);
    }

    /**
     * @param viewerIp 게시판 첫 쪽에 비회원 작성자 본인의 금칙어 보류 글을 끼워 넣을 때 쓴다 (IKA-96)
     */
    public CursorPageResponse<PostListResponse> searchPostsByOffset(
            PostSearchRequest request, CustomUserDetails userDetails, String viewerIp) {
        if (request.page() != null && request.page() > MAX_OFFSET_PAGE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_LIMIT);
        }
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        PostSearchRequest scoped = scopeInquiry(request, userDetails);
        boolean firstPage = scoped.page() == null || scoped.page() <= 1;
        return withViewerHeldPosts(
                scoped,
                firstPage,
                userDetails,
                viewerIp,
                withAnswered(scoped, postRepository.findPostsByOffset(scoped)));
    }

    public CursorPageResponse<PostListResponse> searchPostsByCursor(PostSearchRequest request) {
        return searchPostsByCursor(request, null);
    }

    public CursorPageResponse<PostListResponse> searchPostsByCursor(
            PostSearchRequest request, CustomUserDetails userDetails) {
        return searchPostsByCursor(request, userDetails, null);
    }

    public CursorPageResponse<PostListResponse> searchPostsByCursor(
            PostSearchRequest request, CustomUserDetails userDetails, String viewerIp) {
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        PostSearchRequest scoped = scopeInquiry(request, userDetails);
        boolean firstPage = scoped.cursor() == null || scoped.cursor().isBlank();
        return withViewerHeldPosts(
                scoped,
                firstPage,
                userDetails,
                viewerIp,
                withAnswered(scoped, postRepository.findPostsByCursor(scoped)));
    }

    /**
     * 작성자 본인이 게시판 첫 쪽에서 자기 금칙어 보류 글을 평소처럼 보게 끼워 넣는다 (IKA-96). 검색, 인기글, 1:1 문의, 둘째 쪽부터는 넣지 않는다. 첫 쪽이
     * 꽉 찼으면 마지막 글보다 새 글만 넣어 다음 쪽과 겹치지 않게 한다. 전체 건수와 쪽 수는 바꾸지 않는다(본인 글 몇 건 차이).
     */
    private CursorPageResponse<PostListResponse> withViewerHeldPosts(
            PostSearchRequest request,
            boolean firstPage,
            CustomUserDetails viewer,
            String viewerIp,
            CursorPageResponse<PostListResponse> page) {
        if (!firstPage
                || PostCategoryCodes.isInquiry(request.categoryCode())
                || (request.keyword() != null && !request.keyword().isBlank())
                || request.viewType() == PostViewType.BEST) {
            return page;
        }
        String categoryCode = request.categoryCode();
        List<PostListResponse> held =
                contentFilter.heldPostsOfViewer(viewer, viewerIp).stream()
                        .filter(post -> isListedIn(post.getCategory().getCode(), categoryCode))
                        .map(PostListResponse::from)
                        .toList();
        if (held.isEmpty()) {
            return page;
        }
        boolean full = page.content().size() >= request.size();
        LocalDateTime oldestShown =
                page.content().isEmpty() ? null : page.content().getLast().createdAt();
        List<PostListResponse> merged = new ArrayList<>(page.content());
        for (PostListResponse item : held) {
            if (!full || oldestShown == null || item.createdAt().isAfter(oldestShown)) {
                merged.add(item);
            }
        }
        if (merged.size() == page.content().size()) {
            return page;
        }
        merged.sort(
                Comparator.comparing(
                                PostListResponse::createdAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PostListResponse::publicId));
        return new CursorPageResponse<>(List.copyOf(merged), page.pageInfo());
    }

    private static boolean isListedIn(String postCategory, String requestedCategory) {
        if (requestedCategory != null && !requestedCategory.isBlank()) {
            return postCategory.equalsIgnoreCase(requestedCategory);
        }
        return !PostCategoryCodes.HIDDEN_FROM_BOARD_LIST.contains(postCategory);
    }

    /** 1:1 문의 목록은 로그인이 필요하고, 관리자가 아니면 본인 글로 좁힌다. 요청에 담긴 작성자 값은 믿지 않는다. */
    private PostSearchRequest scopeInquiry(
            PostSearchRequest request, CustomUserDetails userDetails) {
        if (!PostCategoryCodes.isInquiry(request.categoryCode())) {
            return request.withWriterPublicId(null);
        }
        if (!InquiryAccess.isSessionUser(userDetails)) {
            throw new CustomAuthException(ErrorCode.INQUIRY_LOGIN_REQUIRED);
        }
        return request.withWriterPublicId(isAdmin(userDetails) ? null : userDetails.getPublicId());
    }

    private CursorPageResponse<PostListResponse> withAnswered(
            PostSearchRequest request, CursorPageResponse<PostListResponse> page) {
        if (!PostCategoryCodes.isInquiry(request.categoryCode()) || page.content().isEmpty()) {
            return page;
        }
        Set<String> answered =
                postRepository.findPublicIdsAnsweredByAdmin(
                        page.content().stream().map(PostListResponse::publicId).toList());
        List<PostListResponse> content =
                page.content().stream()
                        .map(item -> item.withAnswered(answered.contains(item.publicId())))
                        .toList();
        return new CursorPageResponse<>(content, page.pageInfo());
    }

    @Transactional
    public PostResponse updatePost(
            String publicId, PostUpdateRequest request, CustomUserDetails userDetails) {
        Post post =
                postRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        // 금칙어 보류 글도 작성자는 평소처럼 고칠 수 있다 (IKA-96). 권한 검사는 아래에서 같다.
        if (post.isDeleted()
                || (post.getStatus() != PostStatus.NORMAL
                        && post.getStatus() != PostStatus.FILTER_HOLD)) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        InquiryAccess.requireReadable(post, userDetails);
        // 수정 권한: 작성자 본인만 수정 가능 (관리자 수정 불가)
        validateEditPermission(post, request.anonymousPassword(), userDetails);

        PostCategory category =
                categoryRepository
                        .findByCode(request.categoryCode())
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));
        rejectOwnScreenPost(post);
        rejectOwnScreenCategory(category);
        rejectInquiryBoundaryChange(post.getCategory(), category);
        boolean moving = !category.getCode().equalsIgnoreCase(post.getCategory().getCode());
        // 닫히거나 삭제한 게시판의 글은 그 자리에서 고칠 수 있지만, 다른 글을 그리로 옮겨 올 수는 없다.
        if (moving) {
            requireOpenBoard(category);
        }
        requireAdminBoardWriter(post.getCategory(), userDetails);
        requireAdminBoardWriter(category, userDetails);

        if (category.forbidsAnonymous() && post.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        // 비회원 글을 수정으로 다른 게시판에 옮겨 작성 제한을 우회하지 못하게 한다.
        if (post.getMember() == null && moving && !category.allowsGuest()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        // 본문은 화면에서 HTML로 그려진다. 저장 전에 위험한 태그와 주소를 글자로 바꾼다.
        String content = MarkdownBodySanitizer.sanitize(request.content());
        imageLifecycle.rejectNewInlineImages(content, post.getContent());
        imageLifecycle.protectExisting(
                request.imageUrls(),
                content,
                userDetails == null ? null : userDetails.getUsername(),
                post.getImages().stream().map(PostImage::getImageUrl).toList(),
                post.getContent());
        post.update(request.title(), content, category);
        post.replaceContentImageKeys(imageLifecycle.collectKeys(List.of(), content));
        if (request.imageUrls() != null) {
            post.replaceImages(toPostImages(request.imageUrls()));
        }
        applyFilterAfterEdit(post, userDetails);
        searchOutbox.postChanged(post);
        moderation.postWritten(post);
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(
            String publicId, String anonymousPassword, CustomUserDetails userDetails) {
        Post post =
                postRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        rejectOwnScreenPost(post);
        InquiryAccess.requireReadable(post, userDetails);

        boolean isAdmin = isAdmin(userDetails);

        validateDeletePermission(post, anonymousPassword, userDetails, isAdmin);

        boolean wasHeld = post.getStatus() == PostStatus.FILTER_HOLD;
        post.softDelete(userDetails != null ? userDetails.getMemberId() : null);
        imageLifecycle.releasePostImages(post);
        searchOutbox.postChanged(post);
        if (wasHeld) {
            contentFilter.clearHold(FilterTargetType.POST, post.getId());
        }
    }

    /**
     * 고친 제목과 본문을 다시 검사한다 (IKA-96). 금칙어가 새로 들어가면 보류하고, 보류 글을 깨끗하게 고치면 다시 공개한다. 신고 임시조치 같은 다른 상태는 여기서
     * 바꾸지 않는다(수정 자체가 NORMAL, FILTER_HOLD만 받는다).
     */
    private void applyFilterAfterEdit(Post post, CustomUserDetails editor) {
        Optional<String> matched =
                contentFilter.inspect(editor, post.getTitle(), post.getContent());
        if (matched.isPresent()) {
            post.changeStatus(PostStatus.FILTER_HOLD);
            contentFilter.recordPostHold(post, matched.get());
        } else if (post.getStatus() == PostStatus.FILTER_HOLD) {
            post.changeStatus(PostStatus.NORMAL);
            contentFilter.clearHold(FilterTargetType.POST, post.getId());
        }
    }

    private void validateEditPermission(
            Post post, String anonymousPassword, CustomUserDetails userDetails) {
        if (post.isAnonymous()) {
            // 로그인 유저 본인이 쓴 익명 글인 경우 수정 허용
            if (userDetails != null
                    && post.getMember() != null
                    && post.getMember().getPublicId().equals(userDetails.getPublicId())) {
                return;
            }
            // 비밀번호 기반 익명글 수정 검증
            if (post.getAnonymousPassword() == null
                    || !org.springframework.util.StringUtils.hasText(anonymousPassword)
                    || !passwordEncoder.matches(anonymousPassword, post.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
            boolean isWriter =
                    post.getMember() != null
                            && post.getMember().getPublicId().equals(userDetails.getPublicId());
            if (!isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    private void validateDeletePermission(
            Post post, String anonymousPassword, CustomUserDetails userDetails, boolean isAdmin) {
        if (post.isAnonymous()) {
            if (isAdmin
                    || (userDetails != null
                            && post.getMember() != null
                            && post.getMember().getPublicId().equals(userDetails.getPublicId()))) {
                return;
            }
            if (post.getAnonymousPassword() == null
                    || !org.springframework.util.StringUtils.hasText(anonymousPassword)
                    || !passwordEncoder.matches(anonymousPassword, post.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
            boolean isWriter =
                    post.getMember() != null
                            && post.getMember().getPublicId().equals(userDetails.getPublicId());
            if (!isAdmin && !isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    private String resolveWriterIp(String clientIp) {
        return clientIp != null && !clientIp.isBlank() ? clientIp : DEFAULT_WRITER_IP;
    }

    private void rejectMarketPost(Post post) {
        if (PostCategoryCodes.MARKET.equalsIgnoreCase(post.getCategory().getCode())) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void rejectOwnScreenPost(Post post) {
        if (PostCategoryCodes.hasOwnScreen(post.getCategory().getCode())) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void rejectOwnScreenCategory(PostCategory category) {
        if (PostCategoryCodes.hasOwnScreen(category.getCode())) {
            throw new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND);
        }
    }

    /** 관리자만 쓰는 게시판(공지 등)은 쓰기, 수정, 옮기기 모두 관리자만 한다. */
    private void requireAdminBoardWriter(PostCategory category, CustomUserDetails userDetails) {
        if (category.isAdminOnly() && !isAdmin(userDetails)) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void requireOpenBoard(PostCategory category) {
        if (!category.acceptsNewPosts()) {
            throw new CustomAuthException(ErrorCode.BOARD_CLOSED);
        }
    }

    /** 문의에서 나가면 관리자 답변이 공개되고, 문의로 들어오면 기존 댓글 작성자가 자기 댓글을 못 본다. */
    private void rejectInquiryBoundaryChange(PostCategory current, PostCategory requested) {
        if (PostCategoryCodes.isInquiry(current.getCode())
                != PostCategoryCodes.isInquiry(requested.getCode())) {
            throw new CustomAuthException(ErrorCode.INQUIRY_CATEGORY_CHANGE_NOT_ALLOWED);
        }
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return InquiryAccess.isAdmin(userDetails);
    }

    private PostListResponse toPostListResponse(Post post) {
        return PostListResponse.from(post);
    }

    private List<PostImage> toPostImages(List<String> imageUrls) {
        List<String> storageValues =
                imageUrls.stream().map(imageUrlResolver::toStorageValue).toList();
        // 외부 주소를 받으면 추적 픽셀이나 http 혼합 콘텐츠가 글에 붙는다. 우리 업로드 키만 받는다.
        if (storageValues.stream().anyMatch(value -> !imageUrlResolver.isManagedOriginal(value))
                || storageValues.stream().distinct().count() != storageValues.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        List<PostImage> images = new ArrayList<>();
        int sortOrder = FIRST_IMAGE_SORT_ORDER;
        for (String storageValue : storageValues) {
            images.add(PostImage.builder().imageUrl(storageValue).sortOrder(sortOrder++).build());
        }
        return images;
    }
}
