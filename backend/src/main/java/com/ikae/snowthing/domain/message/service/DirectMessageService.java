package com.ikae.snowthing.domain.message.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberBlockRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.message.dto.DirectMessageDetailResponse;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendRequest;
import com.ikae.snowthing.domain.message.dto.DirectMessageSendResponse;
import com.ikae.snowthing.domain.message.dto.DirectMessageSummaryResponse;
import com.ikae.snowthing.domain.message.dto.MessageBox;
import com.ikae.snowthing.domain.message.dto.MessageListingResponse;
import com.ikae.snowthing.domain.message.dto.MessageMemberResponse;
import com.ikae.snowthing.domain.message.dto.UnreadMessageCountResponse;
import com.ikae.snowthing.domain.message.entity.DirectMessage;
import com.ikae.snowthing.domain.message.repository.DirectMessageRepository;
import com.ikae.snowthing.domain.message.repository.MessageListingRef;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 회원 간 1:1 쪽지. 설계: docs/conception/022_direct_message/01_design.md
 *
 * <p>남의 쪽지와 내 쪽에서 지운 쪽지는 존재 여부를 숨기려고 모두 404로 답한다. 받는 회원이 나를 차단했거나 활동 중이 아니면 같은 문구("보낼 수 없는 회원입니다")로
 * 거부해 차단 사실을 드러내지 않는다. AI 회원은 쪽지로 구분되면 안 되므로 일반 회원과 똑같이 받는다.
 */
@Service
@RequiredArgsConstructor
public class DirectMessageService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DirectMessageRepository directMessageRepository;
    private final MemberRepository memberRepository;
    private final MemberBlockRepository memberBlockRepository;
    private final MarketListingRepository marketListingRepository;

    @Transactional
    public DirectMessageSendResponse send(
            CustomUserDetails userDetails, DirectMessageSendRequest request) {
        Long senderId = userDetails.getMember().getId();
        Member receiver =
                memberRepository
                        .findByPublicId(request.receiverPublicId())
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.MESSAGE_RECEIVER_UNAVAILABLE));
        if (receiver.getId().equals(senderId)) {
            throw new CustomException(ErrorCode.MESSAGE_SELF_NOT_ALLOWED);
        }
        if (receiver.getStatus() != MemberStatus.ACTIVE
                || memberBlockRepository.existsByPair(receiver.getId(), senderId)) {
            throw new CustomException(ErrorCode.MESSAGE_RECEIVER_UNAVAILABLE);
        }
        if (memberBlockRepository.existsByPair(senderId, receiver.getId())) {
            throw new CustomException(ErrorCode.MESSAGE_RECEIVER_BLOCKED);
        }
        Long marketListingId = resolveMarketListing(request.marketListingPublicId(), receiver);

        String content = request.content().strip();
        if (content.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "쪽지 내용을 입력해 주세요.");
        }
        DirectMessage saved =
                directMessageRepository.save(
                        new DirectMessage(
                                memberRepository.getReferenceById(senderId),
                                receiver,
                                marketListingId,
                                content));
        return new DirectMessageSendResponse(saved.getId().toString(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<DirectMessageSummaryResponse> getReceived(
            CustomUserDetails userDetails, Long cursor, Integer size) {
        Long memberId = userDetails.getMember().getId();
        int pageSize = validatePageSize(size);
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<DirectMessage> rows =
                cursor == null
                        ? directMessageRepository.findReceivedFirstPage(memberId, limit)
                        : directMessageRepository.findReceivedAfter(memberId, cursor, limit);
        return toPage(rows, pageSize, MessageBox.RECEIVED);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<DirectMessageSummaryResponse> getSent(
            CustomUserDetails userDetails, Long cursor, Integer size) {
        Long memberId = userDetails.getMember().getId();
        int pageSize = validatePageSize(size);
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<DirectMessage> rows =
                cursor == null
                        ? directMessageRepository.findSentFirstPage(memberId, limit)
                        : directMessageRepository.findSentAfter(memberId, cursor, limit);
        return toPage(rows, pageSize, MessageBox.SENT);
    }

    /** 받은 회원이 처음 열 때만 읽음 시각을 기록한다. */
    @Transactional
    public DirectMessageDetailResponse getDetail(CustomUserDetails userDetails, Long messageId) {
        Long memberId = userDetails.getMember().getId();
        DirectMessage message = findVisible(messageId, memberId);
        message.markReadBy(memberId, LocalDateTime.now(KST));

        boolean received = message.isReceiver(memberId);
        Member counterpart = received ? message.getSender() : message.getReceiver();
        MessageMemberResponse counterpartResponse = MessageMemberResponse.from(counterpart);
        return new DirectMessageDetailResponse(
                message.getId().toString(),
                received ? MessageBox.RECEIVED : MessageBox.SENT,
                counterpartResponse,
                message.getContent(),
                message.isRead(),
                message.getReadAt(),
                listingOf(message.getMarketListingId()),
                received && !counterpartResponse.withdrawn(),
                message.getCreatedAt());
    }

    /** 내 쪽에서만 지운다. 상대 쪽지함에는 그대로 남는다. */
    @Transactional
    public void delete(CustomUserDetails userDetails, Long messageId) {
        Long memberId = userDetails.getMember().getId();
        findVisible(messageId, memberId).deleteBy(memberId, LocalDateTime.now(KST));
    }

    @Transactional(readOnly = true)
    public UnreadMessageCountResponse countUnread(CustomUserDetails userDetails) {
        return new UnreadMessageCountResponse(
                directMessageRepository.countUnread(userDetails.getMember().getId()));
    }

    private DirectMessage findVisible(Long messageId, Long memberId) {
        return directMessageRepository
                .findWithMembersById(messageId)
                .filter(message -> message.isVisibleTo(memberId))
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));
    }

    /** 판매글에서 보낸 쪽지면 판매글을 찾아 받는 회원이 판매글 작성자인지 확인한다. */
    private Long resolveMarketListing(String marketListingPublicId, Member receiver) {
        if (!StringUtils.hasText(marketListingPublicId)) {
            return null;
        }
        MarketListing listing =
                marketListingRepository
                        .findDetailByPostPublicId(marketListingPublicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND));
        Post post = listing.getPost();
        if (post.isDeleted() || post.getStatus() != PostStatus.NORMAL) {
            throw new CustomException(ErrorCode.MARKET_LISTING_NOT_FOUND);
        }
        if (post.getMember() == null || !post.getMember().getId().equals(receiver.getId())) {
            throw new CustomException(ErrorCode.MESSAGE_MARKET_RECEIVER_MISMATCH);
        }
        return listing.getId();
    }

    private CursorPageResponse<DirectMessageSummaryResponse> toPage(
            List<DirectMessage> rows, int pageSize, MessageBox box) {
        boolean hasNext = rows.size() > pageSize;
        List<DirectMessage> page = hasNext ? rows.subList(0, pageSize) : rows;
        Map<Long, MessageListingRef> listings =
                loadListings(page.stream().map(DirectMessage::getMarketListingId).toList());
        List<DirectMessageSummaryResponse> content =
                page.stream()
                        .map(
                                message ->
                                        new DirectMessageSummaryResponse(
                                                message.getId().toString(),
                                                box,
                                                MessageMemberResponse.from(
                                                        box == MessageBox.RECEIVED
                                                                ? message.getSender()
                                                                : message.getReceiver()),
                                                DirectMessageSummaryResponse.preview(
                                                        message.getContent()),
                                                message.isRead(),
                                                message.getMarketListingId() == null
                                                        ? null
                                                        : MessageListingResponse.of(
                                                                listings.get(
                                                                        message
                                                                                .getMarketListingId())),
                                                message.getCreatedAt()))
                        .toList();
        String nextCursor = hasNext ? page.get(page.size() - 1).getId().toString() : null;
        return new CursorPageResponse<>(
                content, CursorPageResponse.PageInfo.ofCursor(nextCursor, hasNext, pageSize));
    }

    private MessageListingResponse listingOf(Long marketListingId) {
        if (marketListingId == null) {
            return null;
        }
        return MessageListingResponse.of(
                loadListings(List.of(marketListingId)).get(marketListingId));
    }

    /** 한 페이지의 판매글을 한 번에 읽는다(N+1 방지). 볼 수 없는 판매글은 결과에 없다. */
    private Map<Long, MessageListingRef> loadListings(List<Long> marketListingIds) {
        List<Long> ids = marketListingIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return directMessageRepository.findVisibleListings(ids).stream()
                .collect(Collectors.toMap(MessageListingRef::marketListingId, Function.identity()));
    }

    private int validatePageSize(Integer size) {
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.MESSAGE_INVALID_PAGE_SIZE);
        }
        return pageSize;
    }
}
