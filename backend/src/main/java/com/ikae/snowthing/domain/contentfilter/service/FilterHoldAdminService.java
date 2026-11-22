package com.ikae.snowthing.domain.contentfilter.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.comment.repository.CommentRepository;
import com.ikae.snowthing.domain.contentfilter.dto.FilterHoldItem;
import com.ikae.snowthing.domain.contentfilter.entity.ContentFilterHold;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;
import com.ikae.snowthing.domain.contentfilter.repository.ContentFilterHoldRepository;
import com.ikae.snowthing.domain.contentfilter.repository.FilterHoldQueryRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.domain.post.repository.PostRepository;
import com.ikae.snowthing.domain.post.service.PostService;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 금칙어 보류 검토 (IKA-96). 공개 전환은 원래 공개 글처럼 되돌리고(검색 색인, 댓글 수), 삭제는 일반 삭제 흐름을 탄다. 보류 줄은 처리하면 지우고 조치 기록에
 * 남긴다. 같은 항목을 두 관리자가 동시에 처리하지 않게 보류 줄을 잠그고 읽는다.
 */
@Service
@RequiredArgsConstructor
public class FilterHoldAdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int HOLD_BAN_DAYS = 30;

    private final ContentFilterHoldRepository holds;
    private final FilterHoldQueryRepository holdQuery;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostService postService;
    private final SearchOutboxWriter searchOutbox;
    private final ChatIpBanService chatIpBanService;
    private final AdminActionLogger actionLogger;

    @Transactional(readOnly = true)
    public AdminPage<FilterHoldItem> getHolds(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<FilterHoldItem> fetched = holdQuery.findPage(safePage * safeSize, safeSize + 1);
        return AdminPage.of(fetched, safePage, safeSize);
    }

    /** 공개 전환. 원문이 이미 다른 상태(작성자가 고쳐 풀렸거나 지움)면 보류 줄만 정리한다. */
    @Transactional
    public void release(Long holdId) {
        ContentFilterHold hold = lockHold(holdId);
        if (hold.getTargetType() == FilterTargetType.POST) {
            postRepository
                    .findById(hold.getTargetId())
                    .filter(post -> post.getStatus() == PostStatus.FILTER_HOLD)
                    .ifPresent(
                            post -> {
                                post.changeStatus(PostStatus.NORMAL);
                                searchOutbox.postChanged(post);
                                actionLogger.record(
                                        AdminActionType.FILTER_HOLD_RELEASE,
                                        AdminTargetType.POST,
                                        post.getPublicId(),
                                        hold.getMatchedWord());
                            });
            holds.delete(hold);
            return;
        }
        Comment comment = commentRepository.findById(hold.getTargetId()).orElse(null);
        holds.delete(hold);
        if (comment == null || !comment.isFilterHeld()) {
            return;
        }
        comment.releaseFilterHold();
        commentRepository.flush();
        searchOutbox.commentChanged(comment, false);
        actionLogger.record(
                AdminActionType.FILTER_HOLD_RELEASE,
                AdminTargetType.COMMENT,
                comment.getId(),
                hold.getMatchedWord());
        // 영속성 컨텍스트를 비우는 벌크 갱신이라 마지막에 둔다.
        postRepository.increaseCommentCount(comment.getPost().getId());
    }

    /** 삭제. 글은 일반 삭제 흐름(사진 정리, 검색 반영)을 타고, 댓글은 아무에게도 보이지 않게 끝낸다. */
    @Transactional
    public void delete(Long holdId, CustomUserDetails admin) {
        ContentFilterHold hold = lockHold(holdId);
        String matchedWord = hold.getMatchedWord();
        if (hold.getTargetType() == FilterTargetType.POST) {
            Post post = postRepository.findById(hold.getTargetId()).orElse(null);
            holds.delete(hold);
            if (post == null) {
                return;
            }
            String publicId = post.getPublicId();
            postService.deletePost(publicId, null, admin);
            actionLogger.record(
                    AdminActionType.FILTER_HOLD_DELETE,
                    AdminTargetType.POST,
                    publicId,
                    matchedWord);
            return;
        }
        Comment comment = commentRepository.findById(hold.getTargetId()).orElse(null);
        holds.delete(hold);
        if (comment == null || !comment.isFilterHeld()) {
            return;
        }
        comment.rejectFilterHold();
        actionLogger.record(
                AdminActionType.FILTER_HOLD_DELETE,
                AdminTargetType.COMMENT,
                comment.getId(),
                matchedWord);
    }

    /** 보류 항목 작성자의 IP 대역을 30일 막는다. 보류 항목은 그대로 두어 관리자가 따로 삭제한다. */
    @Transactional
    public ChatBannedIpResponse banWriterRange(Long holdId, String createdBy) {
        ContentFilterHold hold = lockHold(holdId);
        String reason =
                "금칙어 보류 "
                        + hold.getTargetType().name()
                        + " "
                        + hold.getTargetId()
                        + " ("
                        + hold.getMatchedWord()
                        + ")";
        return chatIpBanService.banWriterRange(
                hold.getWriterIp(), reason, LocalDateTime.now().plusDays(HOLD_BAN_DAYS), createdBy);
    }

    private ContentFilterHold lockHold(Long holdId) {
        return holds.findByIdForUpdate(holdId)
                .orElseThrow(() -> new CustomException(ErrorCode.FILTER_HOLD_NOT_FOUND));
    }
}
