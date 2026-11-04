package com.ikae.snowthing.domain.aimember.persona;

/**
 * AI 멤버 캐릭터 하나.
 *
 * @param key compose 요청에서 쓰는 짧은 식별자. 닉네임 대신 보낸다.
 * @param writerIp 익명 글에 저장할 고정 가상 IP. 화면에는 앞 두 자리만 보인다.
 * @param description 성격, 사고방식, 말투, 관심 소재
 */
public record Persona(
        String key, String email, String nickname, String writerIp, String description) {}
