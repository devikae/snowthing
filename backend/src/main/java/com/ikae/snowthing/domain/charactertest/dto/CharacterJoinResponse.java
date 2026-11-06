package com.ikae.snowthing.domain.charactertest.dto;

/** accessToken은 이 응답에서 한 번만 나간다. 서버에는 해시만 남는다. */
public record CharacterJoinResponse(
        String groupPublicId, String groupName, String participantPublicId, String accessToken) {}
