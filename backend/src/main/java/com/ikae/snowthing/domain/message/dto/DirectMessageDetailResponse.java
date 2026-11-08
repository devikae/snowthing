package com.ikae.snowthing.domain.message.dto;

import java.time.LocalDateTime;

/** 쪽지 상세. canReply는 받은 쪽지이고 보낸 회원이 탈퇴하지 않았을 때 true다. 실제 발송 가능 여부는 보낼 때 다시 검사한다. */
public record DirectMessageDetailResponse(
        String id,
        MessageBox box,
        MessageMemberResponse counterpart,
        String content,
        boolean read,
        LocalDateTime readAt,
        MessageListingResponse marketListing,
        boolean canReply,
        LocalDateTime createdAt) {}
