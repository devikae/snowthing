package com.ikae.snowthing.domain.post.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.post.dto.ScrapPostResponse;
import com.ikae.snowthing.domain.post.dto.ScrapResponse;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;
import com.ikae.snowthing.domain.post.entity.PostScrap;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.repository.PostScrapRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.common.dto.CursorPageResponse.PageInfo;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.util.CursorUtils;
import com.ikae.snowthing.global.util.CursorUtils.CursorValue;

import lombok.RequiredArgsConstructor;

/**
 * 글 스크랩. 설계: docs/conception/026_post_scrap/01_design.md
 *
 * <p>추가와 취소는 모두 멱등하다. 같은 글을 두 번 스크랩해도 UNIQUE (member_id, post_id)와 ON DUPLICATE KEY로 한 줄만 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostScrapService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int SINGLE_ROW_AFFECTED = 1;

    private final PostScrapRepository scrapRepository;
    private final PostRepository postRepository;
    private final Clock clock;

    @Transactional
    public ScrapResponse scrap(String postPublicId, CustomUserDetails userDetails) {
        Long memberId = requireMemberId(userDetails);
        Post post = findScrappablePost(postPublicId);
        // DATETIME(6)에 저장되는 값과 커서 값이 같도록 마이크로초로 자른다.
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MICROS);
        int inserted = scrapRepository.insertIgnoringDuplicate(memberId, post.getId(), now);
        return new ScrapResponse(true, inserted == SINGLE_ROW_AFFECTED);
    }

    /** 글 상태는 보지 않는다. 삭제된 글이나 없는 글이어도 내 행만 지우고 성공한다. */
    @Transactional
    public ScrapResponse unscrap(String postPublicId, CustomUserDetails userDetails) {
        Long memberId = requireMemberId(userDetails);
        int deleted = scrapRepository.deleteByMemberAndPostPublicId(memberId, postPublicId);
        return new ScrapResponse(false, deleted > 0);
    }

    /** 글 상세의 scrapped 값. 비로그인과 위임 요청은 항상 false다. */
    public boolean isScrapped(String postPublicId, CustomUserDetails userDetails) {
        Long memberId = memberIdOrNull(userDetails);
        return memberId != null
                && scrapRepository.existsByMemberAndPostPublicId(memberId, postPublicId);
    }

    public CursorPageResponse<ScrapPostResponse> getMyScraps(
            CustomUserDetails userDetails, String cursor, Integer size) {
        Long memberId = requireMemberId(userDetails);
        int pageSize = normalizeSize(size);
        CursorValue cursorValue = CursorUtils.decode(cursor);

        List<PostScrap> rows =
                scrapRepository.findVisibleByMember(
                        memberId,
                        cursorValue != null ? cursorValue.createdAt() : null,
                        cursorValue != null ? cursorValue.id() : null,
                        pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<PostScrap> page = hasNext ? rows.subList(0, pageSize) : rows;

        String nextCursor = null;
        if (hasNext) {
            PostScrap last = page.get(page.size() - 1);
            nextCursor = CursorUtils.encode(0L, last.getCreatedAt(), last.getId());
        }
        return new CursorPageResponse<>(
                page.stream().map(ScrapPostResponse::from).toList(),
                PageInfo.ofCursor(nextCursor, hasNext, pageSize));
    }

    private Post findScrappablePost(String postPublicId) {
        Post post =
                postRepository
                        .findByPublicId(postPublicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.POST_NOT_FOUND));
        // 1:1 문의는 남의 문의 존재를 드러내지 않도록 작성자에게도 같은 404를 준다.
        if (post.isDeleted()
                || post.getStatus() != PostStatus.NORMAL
                || PostCategoryCodes.isInquiry(post)) {
            throw new CustomAuthException(ErrorCode.POST_NOT_FOUND);
        }
        if (PostCategoryCodes.hasOwnScreen(post.getCategory().getCode())) {
            throw new CustomAuthException(ErrorCode.SCRAP_NOT_ALLOWED);
        }
        return post;
    }

    private static Long requireMemberId(CustomUserDetails userDetails) {
        Long memberId = memberIdOrNull(userDetails);
        if (memberId == null) {
            throw new CustomAuthException(ErrorCode.SCRAP_LOGIN_REQUIRED);
        }
        return memberId;
    }

    /** 스크랩은 본인 기록이라 세션 로그인만 쓴다. API 토큰과 봇 위임 요청은 비로그인처럼 다룬다. */
    private static Long memberIdOrNull(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails instanceof BotDelegatedUserDetails
                || userDetails instanceof ApiTokenUserDetails
                || userDetails.getMember() == null) {
            return null;
        }
        return userDetails.getMember().getId();
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
