package com.ikae.snowthing.domain.member.event;

/**
 * 사람이 직접 가입한 회원. AI 멤버, 관리자 초기화 계정은 발행하지 않는다.
 *
 * @param method EMAIL 또는 소셜 제공자 이름
 */
public record MemberSignedUpEvent(String nickname, String method) {}
