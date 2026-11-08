package com.ikae.snowthing.domain.post.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.image.service.ImageUrlResolver;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final String ANONYMOUS_CATEGORY_CODE = "ANONYMOUS";
    private static final String CREW_CATEGORY_CODE = "CREW";
    private static final String SEASON_ROOM_CATEGORY_CODE = "SEASON_ROOM";
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

    @Transactional
    public PostResponse createPost(
            PostCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        PostCategory category =
                categoryRepository
                        .findByCode(request.categoryCode())
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));
        rejectOwnScreenCategory(category);
        requireNoticeAdmin(category, userDetails);
        if (PostCategoryCodes.NOTICE.equalsIgnoreCase(category.getCode())
                && request.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }
        if (PostCategoryCodes.isInquiry(category.getCode())
                && !InquiryAccess.isSessionUser(userDetails)) {
            throw new CustomAuthException(ErrorCode.INQUIRY_LOGIN_REQUIRED);
        }

        // 비회원은 익명게시판에만 쓴다. isAnonymous=true로 다른 게시판에 쓰는 API 경로를 막는다.
        if (userDetails == null && !ANONYMOUS_CATEGORY_CODE.equalsIgnoreCase(category.getCode())) {
            throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (isAnonymousDisallowedCategory(category.getCode())) {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
            }
            if (request.isAnonymous()) {
                throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
            }
        }

        Member member = null;
        String encodedPassword = null;

        boolean isAnon =
                request.isAnonymous()
                        || ANONYMOUS_CATEGORY_CODE.equalsIgnoreCase(category.getCode());

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
                throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
            }
            member =
                    memberRepository
                            .findByPublicId(userDetails.getPublicId())
                            .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
        }

        Post post =
                Post.builder()
                        .member(member)
                        .category(category)
                        .title(request.title())
                        .content(request.content())
                        .writerIp(resolveWriterIp(clientIp))
                        .isAnonymous(isAnon)
                        .anonymousPassword(encodedPassword)
                        .hasImage(false)
                        .build();
        imageLifecycle.rejectNewInlineImages(request.content(), null);
        imageLifecycle.protectAll(
                request.imageUrls(),
                request.content(),
                userDetails == null ? null : userDetails.getUsername());
        post.replaceImages(toPostImages(request.imageUrls()));
        post.replaceContentImageKeys(imageLifecycle.collectKeys(List.of(), request.content()));

        Post saved = postRepository.save(post);
        searchOutbox.postChanged(saved);
        return PostResponse.from(saved);
    }

    @Transactional
    public PostDetailResponse getPostDetail(String publicId, CustomUserDetails userDetails) {
        return getPostDetail(publicId, userDetails, true);
    }

    @Transactional
    public PostDetailResponse getPostDetail(
            String publicId, CustomUserDetails userDetails, boolean shouldIncreaseViewCount) {
        Post post =
                postRepository
                        .findWithMemberAndCategoryByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        rejectMarketPost(post);
        InquiryAccess.requireReadable(post, userDetails);

        if (post.getStatus() != PostStatus.NORMAL) {
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
        if (request.page() != null && request.page() > MAX_OFFSET_PAGE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_LIMIT);
        }
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        PostSearchRequest scoped = scopeInquiry(request, userDetails);
        return withAnswered(scoped, resolveThumbnailUrls(postRepository.findPostsByOffset(scoped)));
    }

    public CursorPageResponse<PostListResponse> searchPostsByCursor(PostSearchRequest request) {
        return searchPostsByCursor(request, null);
    }

    public CursorPageResponse<PostListResponse> searchPostsByCursor(
            PostSearchRequest request, CustomUserDetails userDetails) {
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        PostSearchRequest scoped = scopeInquiry(request, userDetails);
        return withAnswered(scoped, resolveThumbnailUrls(postRepository.findPostsByCursor(scoped)));
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

        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
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
        requireNoticeAdmin(post.getCategory(), userDetails);
        requireNoticeAdmin(category, userDetails);
        if (PostCategoryCodes.NOTICE.equalsIgnoreCase(category.getCode()) && post.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        if (isAnonymousDisallowedCategory(category.getCode()) && post.isAnonymous()) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        // 비회원 글을 수정으로 다른 게시판에 옮겨 작성 제한을 우회하지 못하게 한다.
        if (post.getMember() == null
                && !category.getCode().equalsIgnoreCase(post.getCategory().getCode())
                && !ANONYMOUS_CATEGORY_CODE.equalsIgnoreCase(category.getCode())) {
            throw new CustomAuthException(ErrorCode.ANONYMOUS_POST_NOT_ALLOWED);
        }

        imageLifecycle.rejectNewInlineImages(request.content(), post.getContent());
        imageLifecycle.protectExisting(
                request.imageUrls(),
                request.content(),
                userDetails == null ? null : userDetails.getUsername(),
                post.getImages().stream().map(PostImage::getImageUrl).toList(),
                post.getContent());
        post.update(request.title(), request.content(), category);
        post.replaceContentImageKeys(imageLifecycle.collectKeys(List.of(), request.content()));
        if (request.imageUrls() != null) {
            post.replaceImages(toPostImages(request.imageUrls()));
        }
        searchOutbox.postChanged(post);
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

        post.softDelete();
        imageLifecycle.releasePostImages(post);
        searchOutbox.postChanged(post);
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

    private void requireNoticeAdmin(PostCategory category, CustomUserDetails userDetails) {
        if (PostCategoryCodes.NOTICE.equalsIgnoreCase(category.getCode())
                && !isAdmin(userDetails)) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }
    }

    /** 문의에서 나가면 관리자 답변이 공개되고, 문의로 들어오면 기존 댓글 작성자가 자기 댓글을 못 본다. */
    private void rejectInquiryBoundaryChange(PostCategory current, PostCategory requested) {
        if (PostCategoryCodes.isInquiry(current.getCode())
                != PostCategoryCodes.isInquiry(requested.getCode())) {
            throw new CustomAuthException(ErrorCode.INQUIRY_CATEGORY_CHANGE_NOT_ALLOWED);
        }
    }

    private boolean isAnonymousDisallowedCategory(String categoryCode) {
        return CREW_CATEGORY_CODE.equalsIgnoreCase(categoryCode)
                || SEASON_ROOM_CATEGORY_CODE.equalsIgnoreCase(categoryCode)
                || PostCategoryCodes.isInquiry(categoryCode);
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return InquiryAccess.isAdmin(userDetails);
    }

    private PostListResponse toPostListResponse(Post post) {
        PostListResponse response = PostListResponse.from(post);
        return response.withThumbnailImageUrl(null);
    }

    private CursorPageResponse<PostListResponse> resolveThumbnailUrls(
            CursorPageResponse<PostListResponse> page) {
        List<PostListResponse> content =
                page.content().stream()
                        .map(response -> response.withThumbnailImageUrl(null))
                        .toList();
        return new CursorPageResponse<>(content, page.pageInfo());
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
