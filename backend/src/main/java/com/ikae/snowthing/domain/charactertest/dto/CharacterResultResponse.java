package com.ikae.snowthing.domain.charactertest.dto;

import java.util.List;

import com.ikae.snowthing.domain.charactertest.model.CharacterStats;

/**
 * bestMatch는 궁합표에 찰떡 상대가 없는 유형이면 null이다(현재 표에는 없다). highlightQuestions는 1부터 세는 질문 번호다. badges는 유형과
 * 별개로 붙는 재미 뱃지 코드다(LAUGH_FIRST: 친구가 넘어졌을 때 먼저 웃음).
 */
public record CharacterResultResponse(
        CharacterLabel character,
        CharacterStats stats,
        List<Integer> highlightQuestions,
        CharacterLabel bestMatch,
        List<String> badges) {
    public CharacterResultResponse {
        highlightQuestions = List.copyOf(highlightQuestions);
        badges = List.copyOf(badges);
    }
}
