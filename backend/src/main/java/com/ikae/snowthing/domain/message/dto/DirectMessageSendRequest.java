package com.ikae.snowthing.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.message.entity.DirectMessage;

/** 쪽지 보내기. marketListingPublicId는 중고장터 판매글에서 보낼 때만 넣는 판매글 게시글의 publicId다. */
public record DirectMessageSendRequest(
        @NotBlank(message = "받는 회원을 지정해 주세요.") @Size(max = 36, message = "받는 회원 정보가 올바르지 않습니다.")
                String receiverPublicId,
        @NotBlank(message = "쪽지 내용을 입력해 주세요.")
                @Size(
                        max = DirectMessage.MAX_CONTENT_LENGTH,
                        message = "쪽지는 최대 1000자까지 입력할 수 있습니다.")
                String content,
        @Size(max = 36, message = "판매글 정보가 올바르지 않습니다.") String marketListingPublicId) {}
