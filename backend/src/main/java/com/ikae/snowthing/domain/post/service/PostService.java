package com.ikae.snowthing.domain.post.service;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.dto.*;
import com.ikae.snowthing.domain.post.entity.*;
import com.ikae.snowthing.domain.post.event.PostReactionEvent;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;
import com.ikae.snowthing.domain.post.repository.PostImageRepository;
import com.ikae.snowthing.domain.post.repository.PostReactionRepository;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final PostCategoryRepository categoryRepository;
    private final PostImageRepository imageRepository;
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

        boolean isAnon = request.isAnonymous() || "ANONYMOUS".equalsIgnoreCase(category.getCode());

        if (isAnon) {
            if (request.anonymousPassword() == null || request.anonymousPassword().isBlank()) {
                throw new CustomAuthException(ErrorCode.INVALID_INPUT);
            }
            encodedPassword = passwordEncoder.encode(request.anonymousPassword());
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
            .writerIp(clientIp != null ? clientIp : "127.0.0.1")
            .isAnonymous(isAnon)
            .anonymousPassword(encodedPassword)
            .build();

        Post savedPost = postRepository.save(post);

        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            int sortOrder = 1;
            for (String imageUrl : request.imageUrls()) {
                PostImage image = PostImage.builder()
                    .post(savedPost)
                    .imageUrl(imageUrl)
                    .sortOrder(sortOrder++)
                    .build();
                imageRepository.save(image);
                savedPost.addImage(image);
            }
        }

        return PostResponse.from(savedPost);
    }

    @Transactional
    public PostDetailResponse getPostDetail(String publicId, CustomUserDetails userDetails) {
        Post post = postRepository.findWithMemberAndCategoryByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        if (post.getStatus() != PostStatus.NORMAL) {
            boolean isAdmin = userDetails != null && userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
            }
        }

        post.increaseViewCount();
        return PostDetailResponse.from(post);
    }

    public Page<PostListResponse> getPostList(String categoryCode, int page, int size) {
        if (size < 1 || size > 100) {
            throw new CustomAuthException(ErrorCode.INVALID_PAGE_SIZE);
        }

        Pageable pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Page<Post> posts;
        if (categoryCode != null && !categoryCode.isBlank()) {
            posts = postRepository.findByCategoryCodeWithMemberAndCategory(categoryCode, pageable);
        } else {
            posts = postRepository.findAllWithMemberAndCategory(pageable);
        }

        return posts.map(PostListResponse::from);
    }

    @Transactional
    public PostResponse updatePost(String publicId, PostUpdateRequest request, CustomUserDetails userDetails) {
        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        validateUpdateOrDeletePermission(post, request.anonymousPassword(), userDetails);

        PostCategory category = categoryRepository.findByCode(request.categoryCode())
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_CATEGORY_NOT_FOUND));

        post.update(request.title(), request.content(), category);
        return PostResponse.from(post);
    }

    @Transactional
    public void deletePost(String publicId, String anonymousPassword, CustomUserDetails userDetails) {
        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        validateUpdateOrDeletePermission(post, anonymousPassword, userDetails);

        post.softDelete();
    }

    @Transactional
    public void reactToPost(String publicId, ReactionType type, CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
        }

        Post post = postRepository.findByPublicId(publicId)
            .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));

        if (post.isDeleted()) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }

        Member member = memberRepository.findByPublicId(userDetails.getPublicId())
            .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));

        PostReaction reaction = PostReaction.builder()
            .post(post)
            .member(member)
            .type(type)
            .build();

        try {
            reactionRepository.save(reaction);
        } catch (DataIntegrityViolationException e) {
            log.warn("중복 추천/비추천 투표 감지 - memberId: {}, postId: {}", member.getId(), post.getId());
            throw new CustomAuthException(ErrorCode.ALREADY_REACTED);
        }

        eventPublisher.publishEvent(new PostReactionEvent(post.getId(), type));
    }

    private void validateUpdateOrDeletePermission(Post post, String anonymousPassword, CustomUserDetails userDetails) {
        if (post.isAnonymous()) {
            if (anonymousPassword == null || !passwordEncoder.matches(anonymousPassword, post.getAnonymousPassword())) {
                throw new CustomAuthException(ErrorCode.INVALID_ANON_PASSWORD);
            }
        } else {
            if (userDetails == null) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }

            boolean isAdmin = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            boolean isWriter = post.getMember() != null && post.getMember().getPublicId().equals(userDetails.getPublicId());

            if (!isAdmin && !isWriter) {
                throw new CustomAuthException(ErrorCode.ACCESS_DENIED);
            }
        }
    }
}
