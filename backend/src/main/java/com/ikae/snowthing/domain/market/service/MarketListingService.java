package com.ikae.snowthing.domain.market.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.image.service.ImageUrlResolver;
import com.ikae.snowthing.domain.market.dto.*;
import com.ikae.snowthing.domain.market.entity.*;
import com.ikae.snowthing.domain.market.repository.MarketCategoryRepository;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.PostImage;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostImageRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.common.dto.CursorPageResponse.PageInfo;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketListingService {

    private static final String MARKET_POST_CATEGORY_CODE = "MARKET";
    private static final String DEFAULT_WRITER_IP = "127.0.0.1";
    private static final String ORIGINAL_IMAGE_PREFIX = "public/posts/originals/";
    private static final int FIRST_IMAGE_SORT_ORDER = 1;
    private static final int MAX_IMAGES = 5;
    private static final int MAX_PAGE = 100;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LIMIT = 4;

    private final MarketListingRepository marketListingRepository;
    private final MarketCategoryRepository marketCategoryRepository;
    private final PostRepository postRepository;
    private final PostCategoryRepository postCategoryRepository;
    private final PostImageRepository postImageRepository;
    private final MemberRepository memberRepository;
    private final ImageUrlResolver imageUrlResolver;

    public MarketCategoryListResponse getCategories() {
        return new MarketCategoryListResponse(
                marketCategoryRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc().stream()
                        .map(MarketCategoryResponse::from)
                        .toList());
    }

    public MarketPreviewResponse getPreview() {
        List<MarketListing> listings = marketListingRepository.findLatestVisible(PREVIEW_LIMIT);
        Map<Long, String> thumbnails = findThumbnailUrls(listings);
        return new MarketPreviewResponse(
                listings.stream()
                        .map(
                                listing ->
                                        new MarketPreviewResponse.Item(
                                                listing.getPost().getPublicId(),
                                                thumbnails.get(listing.getPost().getId())))
                        .toList());
    }

    public CursorPageResponse<MarketListingSummaryResponse> search(
            int page,
            int size,
            String keyword,
            String categoryCode,
            ProductCondition productCondition,
            TradeStatus tradeStatus) {
        validatePage(page, size);
        Page<MarketListing> result =
                marketListingRepository.search(
                        keyword,
                        categoryCode,
                        productCondition,
                        tradeStatus,
                        PageRequest.of(page - 1, size));
        Map<Long, String> thumbnails = findThumbnailUrls(result.getContent());
        List<MarketListingSummaryResponse> content =
                result.getContent().stream()
                        .map(
                                listing ->
                                        toSummary(
                                                listing, thumbnails.get(listing.getPost().getId())))
                        .toList();
        return new CursorPageResponse<>(
                content,
                PageInfo.ofOffset(
                        page,
                        result.getTotalPages(),
                        result.getTotalElements(),
                        result.hasNext(),
                        size));
    }

    @Transactional
    public MarketListingCreateResponse create(
            MarketListingCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        Member member = requireMember(userDetails);
        MarketCategory category = requireActiveCategory(request.categoryCode());
        PostCategory postCategory =
                postCategoryRepository
                        .findByCode(MARKET_POST_CATEGORY_CODE)
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_CATEGORY_NOT_FOUND));
        List<PostImage> images = toPostImages(request.imageUrls());

        Post post =
                Post.builder()
                        .member(member)
                        .category(postCategory)
                        .title(request.title().trim())
                        .content(request.content())
                        .writerIp(resolveWriterIp(clientIp))
                        .isAnonymous(false)
                        .hasImage(!images.isEmpty())
                        .build();
        post.replaceImages(images);
        postRepository.save(post);

        MarketListing listing =
                MarketListing.builder()
                        .post(post)
                        .category(category)
                        .productCondition(request.productCondition())
                        .transactionMethod(request.transactionMethod())
                        .price(request.price())
                        .negotiable(request.negotiable())
                        .free(request.free())
                        .contact(request.contact())
                        .build();
        return MarketListingCreateResponse.from(marketListingRepository.save(listing));
    }

    @Transactional
    public MarketListingDetailResponse getDetail(
            String publicId, CustomUserDetails userDetails, boolean increaseViewCount) {
        requireMember(userDetails);
        MarketListing listing = findVisible(publicId);
        Post post = listing.getPost();
        List<String> images =
                postImageRepository.findByPostIdOrderBySortOrderAsc(post.getId()).stream()
                        .map(PostImage::getImageUrl)
                        .map(imageUrlResolver::toPublicUrl)
                        .toList();
        int viewCount = post.getViewCount();
        if (increaseViewCount) {
            postRepository.increaseViewCount(post.getId());
            viewCount++;
        }
        boolean writer = isWriter(post, userDetails);
        return toDetail(listing, images, viewCount, writer, writer || isAdmin(userDetails));
    }

    @Transactional
    public MarketListingUpdateResponse update(
            String publicId, MarketListingUpdateRequest request, CustomUserDetails userDetails) {
        MarketListing listing = findForUpdate(publicId);
        validateWriter(listing.getPost(), userDetails);
        validateVersion(listing, request.version());
        MarketCategory category = requireActiveCategory(request.categoryCode());
        List<PostImage> images = toPostImages(request.imageUrls());

        listing.getPost()
                .update(request.title().trim(), request.content(), listing.getPost().getCategory());
        listing.getPost().replaceImages(images);
        listing.update(
                category,
                request.productCondition(),
                request.transactionMethod(),
                request.price(),
                request.negotiable(),
                request.free(),
                request.contact());
        marketListingRepository.flush();
        return new MarketListingUpdateResponse(
                publicId, listing.getVersion(), listing.getUpdatedAt());
    }

    @Transactional
    public MarketTradeStatusUpdateResponse updateTradeStatus(
            String publicId,
            MarketTradeStatusUpdateRequest request,
            CustomUserDetails userDetails) {
        MarketListing listing =
                marketListingRepository
                        .findForTradeStatusUpdateByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        validateVisible(listing);
        validateWriter(listing.getPost(), userDetails);
        validateVersion(listing, request.version());
        listing.changeTradeStatus(request.tradeStatus());
        marketListingRepository.flush();
        return new MarketTradeStatusUpdateResponse(
                publicId, listing.getTradeStatus(), listing.getVersion(), listing.getUpdatedAt());
    }

    @Transactional
    public void delete(String publicId, CustomUserDetails userDetails) {
        MarketListing listing =
                marketListingRepository
                        .findForUpdateByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        if (!isWriter(listing.getPost(), userDetails) && !isAdmin(userDetails)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        listing.getPost().softDelete();
    }

    @Transactional
    public void updateModerationStatus(
            String publicId, PostStatus status, CustomUserDetails userDetails) {
        if (!isAdmin(userDetails)
                || (status != PostStatus.NORMAL
                        && status != PostStatus.HIDDEN
                        && status != PostStatus.BLOCKED)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        MarketListing listing =
                marketListingRepository
                        .findForUpdateByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        listing.getPost().changeStatus(status);
    }

    private MarketListingSummaryResponse toSummary(
            MarketListing listing, String thumbnailImageUrl) {
        Post post = listing.getPost();
        Member seller = post.getMember();
        return new MarketListingSummaryResponse(
                post.getPublicId(),
                post.getTitle(),
                new MarketListingSummaryResponse.Category(
                        listing.getCategory().getCode(), listing.getCategory().getName()),
                listing.getProductCondition(),
                listing.getTransactionMethod(),
                listing.getTradeStatus(),
                listing.getPrice(),
                listing.isNegotiable(),
                listing.isFree(),
                thumbnailImageUrl,
                new MarketListingSummaryResponse.Seller(
                        seller.getPublicId(), seller.getNickname(), seller.getProfileImageUrl()),
                post.getViewCount(),
                post.getCommentCount(),
                listing.getCreatedAt(),
                listing.getUpdatedAt());
    }

    private MarketListingDetailResponse toDetail(
            MarketListing listing,
            List<String> images,
            int viewCount,
            boolean canEdit,
            boolean canDelete) {
        Post post = listing.getPost();
        Member seller = post.getMember();
        return new MarketListingDetailResponse(
                post.getPublicId(),
                post.getTitle(),
                post.getContent(),
                new MarketListingSummaryResponse.Category(
                        listing.getCategory().getCode(), listing.getCategory().getName()),
                listing.getProductCondition(),
                listing.getTransactionMethod(),
                listing.getTradeStatus(),
                listing.getPrice(),
                listing.isNegotiable(),
                listing.isFree(),
                listing.getContact(),
                new MarketListingSummaryResponse.Seller(
                        seller.getPublicId(), seller.getNickname(), seller.getProfileImageUrl()),
                images,
                viewCount,
                post.getCommentCount(),
                listing.getVersion(),
                canEdit,
                canDelete,
                listing.getCreatedAt(),
                listing.getUpdatedAt());
    }

    private Map<Long, String> findThumbnailUrls(List<MarketListing> listings) {
        if (listings.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = listings.stream().map(item -> item.getPost().getId()).toList();
        Map<Long, String> thumbnails = new LinkedHashMap<>();
        for (PostImage image : postImageRepository.findAllByPostIdsOrderBySortOrder(postIds)) {
            thumbnails.putIfAbsent(
                    image.getPost().getId(),
                    imageUrlResolver.toThumbnailPublicUrl(image.getImageUrl()));
        }
        return thumbnails;
    }

    private List<PostImage> toPostImages(List<String> imageUrls) {
        if (imageUrls.size() > MAX_IMAGES) {
            throw new CustomException(ErrorCode.MARKET_IMAGE_LIMIT_EXCEEDED);
        }
        List<String> storageValues =
                imageUrls.stream().map(imageUrlResolver::toStorageValue).toList();
        if (storageValues.stream().anyMatch(value -> !isAllowedOriginalImage(value))
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

    private boolean isAllowedOriginalImage(String value) {
        return StringUtils.hasText(value)
                && value.startsWith(ORIGINAL_IMAGE_PREFIX)
                && !value.contains("..")
                && !value.contains("\\");
    }

    private MarketListing findVisible(String publicId) {
        MarketListing listing =
                marketListingRepository
                        .findDetailByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        validateVisible(listing);
        return listing;
    }

    private MarketListing findForUpdate(String publicId) {
        MarketListing listing =
                marketListingRepository
                        .findForUpdateByPostPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        validateVisible(listing);
        return listing;
    }

    private void validateVisible(MarketListing listing) {
        Post post = listing.getPost();
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND);
        }
    }

    private MarketCategory requireActiveCategory(String code) {
        MarketCategory category =
                marketCategoryRepository
                        .findByCode(code)
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.MARKET_CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new CustomException(ErrorCode.MARKET_CATEGORY_INACTIVE);
        }
        return category;
    }

    private Member requireMember(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return memberRepository
                .findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateWriter(Post post, CustomUserDetails userDetails) {
        requireMember(userDetails);
        if (!isWriter(post, userDetails)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateVersion(MarketListing listing, long requestedVersion) {
        if (listing.getVersion() != requestedVersion) {
            throw new CustomException(ErrorCode.MARKET_LISTING_UPDATE_CONFLICT);
        }
    }

    private boolean isWriter(Post post, CustomUserDetails userDetails) {
        return userDetails != null
                && post.getMember() != null
                && post.getMember().getPublicId().equals(userDetails.getPublicId());
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null
                && userDetails.getAuthorities().stream()
                        .anyMatch(
                                authority ->
                                        authority.getAuthority().equals(Role.ROLE_ADMIN.getKey()));
    }

    private void validatePage(int page, int size) {
        if (page < 1 || page > MAX_PAGE) {
            throw new CustomException(ErrorCode.MARKET_PAGE_LIMIT_EXCEEDED);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.MARKET_INVALID_PAGE_SIZE);
        }
    }

    private String resolveWriterIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp : DEFAULT_WRITER_IP;
    }
}
