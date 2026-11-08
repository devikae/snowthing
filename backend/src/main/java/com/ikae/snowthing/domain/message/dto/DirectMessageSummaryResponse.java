package com.ikae.snowthing.domain.message.dto;

import java.time.LocalDateTime;

/**
 * 받은함, 보낸함 목록 한 줄. counterpart는 받은함이면 보낸 회원, 보낸함이면 받은 회원이다. read는 수신자가 열었는지다. marketListing은 판매글에서
 * 보낸 쪽지일 때만 채운다.
 */
public record DirectMessageSummaryResponse(
        String id,
        MessageBox box,
        MessageMemberResponse counterpart,
        String preview,
        boolean read,
        MessageListingResponse marketListing,
        LocalDateTime createdAt) {

    public static final int PREVIEW_LENGTH = 80;

    public static String preview(String content) {
        String singleLine = content.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= PREVIEW_LENGTH
                ? singleLine
                : singleLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
