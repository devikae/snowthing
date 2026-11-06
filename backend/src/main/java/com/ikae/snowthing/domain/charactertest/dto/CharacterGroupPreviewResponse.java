package com.ikae.snowthing.domain.charactertest.dto;

/** 링크 미리보기용 공개 정보. 참여자 닉네임은 담지 않는다. */
public record CharacterGroupPreviewResponse(
        String publicId, String name, long participantCount, int maxParticipants) {}
