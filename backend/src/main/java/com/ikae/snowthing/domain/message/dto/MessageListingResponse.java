package com.ikae.snowthing.domain.message.dto;

import com.ikae.snowthing.domain.message.repository.MessageListingRef;

/** 쪽지가 참조한 판매글. 판매글이 삭제됐거나 볼 수 없으면 publicId와 제목을 비우고 deleted를 true로 둔다. */
public record MessageListingResponse(String publicId, String title, boolean deleted) {

    public static MessageListingResponse of(MessageListingRef ref) {
        return ref == null
                ? new MessageListingResponse(null, null, true)
                : new MessageListingResponse(ref.postPublicId(), ref.title(), false);
    }
}
