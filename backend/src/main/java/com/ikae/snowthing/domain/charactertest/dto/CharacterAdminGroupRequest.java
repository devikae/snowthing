package com.ikae.snowthing.domain.charactertest.dto;

/** 운영자가 동호회에 뿌릴 빈 모임을 만든다. 이름 검증은 일반 모임 이름과 같다. */
public record CharacterAdminGroupRequest(String name) {}
