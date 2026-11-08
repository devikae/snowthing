package com.ikae.snowthing.domain.charactertest.service;

import java.util.List;
import java.util.Locale;

/** 금칙어 기능이 아직 없어 코드에 작은 목록을 둔다. 나중에 관리 기능으로 옮긴다. 형식 검사는 요청 DTO가 한다. */
final class CharacterNicknameFilter {

    private static final List<String> FORBIDDEN_WORDS =
            List.of(
                    "시발",
                    "씨발",
                    "씨바",
                    "시바",
                    "병신",
                    "븅신",
                    "지랄",
                    "존나",
                    "좆",
                    "개새",
                    "새끼",
                    "느금",
                    "니미",
                    "섹스",
                    "자지",
                    "보지",
                    "운영자",
                    "관리자",
                    "admin",
                    "눈팅",
                    "snowthing",
                    "fuck",
                    "shit",
                    "sex");

    private CharacterNicknameFilter() {}

    static boolean isForbidden(String nickname) {
        String normalized = nickname.toLowerCase(Locale.ROOT);
        return FORBIDDEN_WORDS.stream().anyMatch(normalized::contains);
    }
}
