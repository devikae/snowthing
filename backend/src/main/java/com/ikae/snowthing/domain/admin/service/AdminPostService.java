package com.ikae.snowthing.domain.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminPostResponse;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.image.service.PostImageLifecycleService;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 게시글 관리. 삭제는 일반 삭제 흐름(PostService)을 그대로 타서 사진 정리와 검색 반영이 빠지지 않게 한다. 블라인드는 시장 글 관리자 조치와 같은 방식으로
 * status를 BLOCKED로 바꾼다. 신고 임시조치(TEMPORARY_HOLD) 중인 글은 신고 처리 화면에서만 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPostService {

    public enum Filter {
        ALL,
        DELETED,
        BLINDED,
        HIDDEN,
        HELD
    }

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final PostService postService;
    private final SearchOutboxWriter searchOutbox;
    private final PostImageLifecycleService imageLifecycle;
    private final AdminActionLogger actionLogger;

    @Transactional(readOnly = true)
    public Page<AdminPostResponse> getPosts(Filter filter, String category, int page, int size) {
        PageRequest pageable =
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        String categoryCode = category == null || category.isBlank() ? null : category.strip();
        if (categoryCode != null && categoryCode.length() > 50) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return postRepository
                .findForAdmin(filter.name(), categoryCode, pageable)
                .map(AdminPostResponse::from);
    }

    @Transactional
    public void deletePost(String publicId, CustomUserDetails admin) {
        Post post =
                postRepository
                        .findIncludingDeletedForUpdate(publicId)
                        .filter(found -> !found.isDeleted())
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        if (PostCategoryCodes.hasOwnScreen(post.getCategory().getCode())) {
            // 시장과 카풀 글은 일반 삭제 흐름이 404로 막는다. 같은 정리(사진 해제, 검색 반영)를 여기서 한다 (IKA-78).
            // 카풀 전용 삭제는 정상 상태 글만 받아 블라인드한 글을 지울 수 없어 쓰지 않는다.
            post.softDelete(admin.getMemberId());
            imageLifecycle.releasePostImages(post);
            searchOutbox.postChanged(post);
        } else {
            postService.deletePost(publicId, null, admin);
        }
        actionLogger.record(AdminActionType.POST_DELETE, AdminTargetType.POST, publicId, null);
        log.info(
                "Post soft-deleted by admin: publicId={}, admin={}", publicId, admin.getPublicId());
    }

    @Transactional
    public void setBlinded(String publicId, boolean blinded) {
        Post post =
                postRepository
                        .findIncludingDeletedForUpdate(publicId)
                        .filter(found -> !found.isDeleted())
                        .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        PostStatus from = blinded ? PostStatus.NORMAL : PostStatus.BLOCKED;
        PostStatus to = blinded ? PostStatus.BLOCKED : PostStatus.NORMAL;
        if (post.getStatus() == to) {
            return;
        }
        if (post.getStatus() != from) {
            throw new CustomException(ErrorCode.CONTENT_REPORT_ACTION_INVALID);
        }
        post.changeStatus(to);
        searchOutbox.postChanged(post);
        actionLogger.record(
                blinded ? AdminActionType.POST_BLIND : AdminActionType.POST_UNBLIND,
                AdminTargetType.POST,
                publicId,
                null);
        log.info("Post blind status updated by admin: publicId={}, blinded={}", publicId, blinded);
    }
}
