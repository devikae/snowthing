package com.ikae.snowthing.domain.chat.dto;

/** 라이브톡 보낸 사람. 프로필 사진은 회원만 있고, 비회원과 봇은 null이라 화면이 기본 그림을 쓴다. */
public record SenderDto(String publicId, String nickname, String profileImageUrl) {

    public SenderDto(String publicId, String nickname) {
        this(publicId, nickname, null);
    }
}
