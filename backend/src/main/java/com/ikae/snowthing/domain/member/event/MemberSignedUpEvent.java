package com.ikae.snowthing.domain.member.event;

/**
 * 사람이 직접 가입한 회원. AI 멤버, 관리자 초기화 계정은 발행하지 않는다.
 *
 * @param method EMAIL 또는 소셜 제공자 이름
 * @param socialName 소셜 제공자가 준 이름(구글 name, 네이버 별명, 카카오 프로필 닉네임). 저장하지 않고 알림에만 쓴다. 이메일 가입이면 null
 */
public record MemberSignedUpEvent(String nickname, String method, String socialName) {}
