package com.ikae.snowthing.domain.post.service;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.event.PostReactionEvent;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private static final String ANONYMOUS_CATEGORY_CODE = "ANONYMOUS";
    private static final String DEFAULT_WRITER_IP = "127.0.0.1";
    private static final int FIRST_IMAGE_SORT_ORDER = 1;
    private static final int MAX_OFFSET_PAGE = 100;
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_ID = "id";

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final PostReactionRepository reactionRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostResponse createPost(PostCreateRequest request, CustomUserDetails userDetails, String clientIp) {
        PostCategory category = categoryRepository.findByCode(request.categoryCode())
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));

        Member member = null;
        String encodedPassword = null;

        boolean isAnon = request.isAnonymous() || ANONYMOUS_CATEGORY_CODE.equalsIgnoreCase(category.getCode());

        if (isAnon) {
            if (userDetails != null) {
                member = memberRepository.findByPublicId(userDetails.getPublicId()).orElse(null);
            }
            if (member == null && (request.anonymousPassword() == null || request.anonymousPassword().isBlank())) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
            if (request.anonymousPassword() != null && !request.anonymousPassword().isBlank()) {
                encodedPassword = passwordEncoder.encode(request.anonymousPassword());
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
            }
            member = memberRepository.findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
        }

        Post post = Post.builder()
            .member(member)
            .category(category)
            .title(request.title())
            .content(request.content())
            .writerIp(resolveWriterIp(clientIp))
            .isAnonymous(isAnon)
            .anonymousPassword(encodedPassword)
            .hasImage(false)
            .build();
        post.replaceImages(toPostImages(request.imageUrls()));

        return PostResponse.from(postRepository.save(post));
    }

    @Transactional
    public PostDetailResponse getPostDetail(String publicId, CustomUserDetails userDetails) {
        return getPostDetail(publicId, userDetails, true);
    }

    @Transactional
    public PostDetailResponse getPostDetail(String publicId, CustomUserDetails userDetails, boolean shouldIncreaseViewCount) {
        Post post = postRepository.findWithMemberAndCategoryByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        if (post.getStatus() != PostStatus.NORMAL) {
            if (!isAdmin(userDetails)) {
                throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
            }
        }

        if (shouldIncreaseViewCount) {
            post.increaseViewCount();
        }
        return PostDetailResponse.from(post);
    }

    public Page<PostListResponse> getPostList(String categoryCode, int page, int size) {
        if (page > MAX_OFFSET_PAGE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_LIMIT);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, SORT_CREATED_AT).and(Sort.by(Sort.Direction.DESC, SORT_ID))
        );

        Page<Post> posts;
        if (categoryCode != null && !categoryCode.isBlank()) {
            posts = postRepository.findByCategoryCodeWithMemberAndCategory(categoryCode, pageable);
        } else {
            posts = postRepository.findAllWithMemberAndCategory(pageable);
        }

        return posts.map(PostListResponse::from);
    }

    public com.ikae.snowthing.global.common.dto.CursorPageResponse<PostListResponse> searchPostsByOffset(PostSearchRequest request) {
        if (request.page() != null && request.page() > MAX_OFFSET_PAGE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_LIMIT);
        }
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        return postRepository.findPostsByOffset(request);
    }

    public com.ikae.snowthing.global.common.dto.CursorPageResponse<PostListResponse> searchPostsByCursor(PostSearchRequest request) {
        if (request.size() < MIN_PAGE_SIZE || request.size() > MAX_PAGE_SIZE) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }
        return postRepository.findPostsByCursor(request);
    }

    @Transactional
    public PostResponse updatePost(String publicId, PostUpdateRequest request, CustomUserDetails userDetails) {
        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        // 수정 권한: 작성자 본인만 수정 가능 (관리자 수정 불가)
        validateEditPermission(post, request.anonymousPassword(), userDetails);

        PostCategory category = categoryRepository.findByCode(request.categoryCode())
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));

        post.update(request.title(), request.content(), category);
        post.replaceImages(toPostImages(request.imageUrls()));
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(String publicId, String anonymousPassword, CustomUserDetails userDetails) {
        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        boolean isAdmin = isAdmin(userDetails);

        validateDeletePermission(post, anonymousPassword, userDetails, isAdmin);

        post.softDelete();
    }

    @Transactional
    public ReactionToggleResponse reactToPost(String publicId, ReactionType type, CustomUserDetails userDetails, String clientIp, String anonymousVoterId) {
        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        Member member = resolveMember(userDetails);
        ReactionActor actor = member != null
            ? ReactionActor.member(member.getId(), resolveWriterIp(clientIp))
            : ReactionActor.anonymous(resolveAnonymousVoterId(anonymousVoterId), resolveWriterIp(clientIp));

        Optional<PostReaction> existing = findExistingReaction(post.getId(), actor, type);

        boolean isToggledOn;
        if (existing.isPresent()) {
            // TOGGLE OFF -> 기존 투표 레코드 삭제 및 해당 카운트 차감
            reactionRepository.delete(existing.get());
            if (type == ReactionType.LIKE) {
                post.decreaseLikeCount();
            } else {
                post.decreaseDislikeCount();
            }
            isToggledOn = false;
        } else {
            // TOGGLE ON -> 신규 투표 레코드 추가 및 해당 카운트 증가
            PostReaction reaction = PostReaction.builder()
                .post(post)
                .member(member)
                .writerIp(actor.writerIp())
                .anonymousVoterId(actor.anonymousVoterId())
                .type(type)
                .build();
            reactionRepository.save(reaction);

            increaseReactionCount(post, type);
            isToggledOn = true;
        }

        eventPublisher.publishEvent(new PostReactionEvent(post.getId(), type));

        String msg = isToggledOn
            ? (type == ReactionType.LIKE ? "추천했습니다!" : "비추천했습니다!")
            : (type == ReactionType.LIKE ? "추천을 취소했습니다." : "비추천을 취소했습니다.");

        return ReactionToggleResponse.builder()
            .isToggledOn(isToggledOn)
            .type(type.name())
            .likeCount(post.getLikeCount())
            .dislikeCount(post.getDislikeCount())
            .message(msg)
            .build();
    }

    private void validateEditPermission(Post post, String anonymousPassword, CustomUserDetails userDetails) {
        if (post.isAnonymous()) {
            // 로그인 유저 본인이 쓴 익명 글인 경우 수정 허용
            if (userDetails != null && post.getMember() != null && post.getMember().getPublicId().equals(userDetails.getPublicId())) {
                return;
            }
            // 비밀번호 기반 익명글 수정 검증
            if (post.getAnonymousPassword() == null || !org.springframework.util.StringUtils.hasText(anonymousPassword)
                    || !passwordEncoder.matches(anonymousPassword, post.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
            boolean isWriter = post.getMember() != null && post.getMember().getPublicId().equals(userDetails.getPublicId());
            if (!isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    private void validateDeletePermission(Post post, String anonymousPassword, CustomUserDetails userDetails, boolean isAdmin) {
        if (post.isAnonymous()) {
            if (isAdmin || (userDetails != null && post.getMember() != null && post.getMember().getPublicId().equals(userDetails.getPublicId()))) {
                return;
            }
            if (post.getAnonymousPassword() == null || !org.springframework.util.StringUtils.hasText(anonymousPassword)
                    || !passwordEncoder.matches(anonymousPassword, post.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
            boolean isWriter = post.getMember() != null && post.getMember().getPublicId().equals(userDetails.getPublicId());
            if (!isAdmin && !isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    private Member resolveMember(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return memberRepository.findByPublicId(userDetails.getPublicId()).orElse(null);
    }

    private Optional<PostReaction> findExistingReaction(Long postId, ReactionActor actor, ReactionType type) {
        if (actor.isMember()) {
            return reactionRepository.findByPostIdAndMemberIdAndType(postId, actor.memberId(), type);
        }
        return reactionRepository.findByPostIdAndAnonymousVoterIdAndType(postId, actor.anonymousVoterId(), type);
    }

    private String resolveAnonymousVoterId(String anonymousVoterId) {
        if (anonymousVoterId == null || anonymousVoterId.isBlank()) {
            throw new CustomAuthException(ErrorCode.INVALID_INPUT);
        }
        return anonymousVoterId;
    }

    private String resolveWriterIp(String clientIp) {
        return clientIp != null && !clientIp.isBlank() ? clientIp : DEFAULT_WRITER_IP;
    }

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null && userDetails.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.getKey()));
    }

    private List<PostImage> toPostImages(List<String> imageUrls) {
        List<PostImage> images = new ArrayList<>();
        int sortOrder = FIRST_IMAGE_SORT_ORDER;
        for (String imageUrl : imageUrls) {
            images.add(PostImage.builder()
                .imageUrl(imageUrl)
                .sortOrder(sortOrder++)
                .build());
        }
        return images;
    }

    private void increaseReactionCount(Post post, ReactionType type) {
        if (type == ReactionType.LIKE) {
            post.increaseLikeCount();
        } else {
            post.increaseDislikeCount();
        }
    }
}
