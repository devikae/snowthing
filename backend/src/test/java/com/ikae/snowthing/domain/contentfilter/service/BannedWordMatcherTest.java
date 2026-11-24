package com.ikae.snowthing.domain.contentfilter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 금칙어 비교 (IKA-96). 060 마이그레이션 초기 목록으로 우회 표기는 잡고 스키 대화는 잡지 않는지 본다. */
class BannedWordMatcherTest {

    private static final List<String> SEED =
            List.of("카지노사이트", "온라인카지노", "바카라", "토토사이트", "슬롯사이트", "홀덤사이트", "먹튀검증", "가입머니");

    private final BannedWordMatcher matcher =
            new BannedWordMatcher(
                    SEED.stream()
                            .map(
                                    word ->
                                            new BannedWordMatcher.Entry(
                                                    word, BannedWordMatcher.normalize(word)))
                            .toList());

    @ParameterizedTest
    @ValueSource(
            strings = {
                "카지노사이트 추천",
                "카 지 노 사 이 트",
                "카.지.노.사.이.트",
                "카-지-노_사/이*트",
                "카​지​노사이트",
                "<p>카지<b>노</b>사이트</p>",
                "카&nbsp;지노&nbsp;사이트",
                "온라인　카지노",
                "가입머니 3만 지급",
                "바♥카♥라"
            })
    @DisplayName("공백, 기호, 태그, 보이지 않는 문자, 전각 공백을 끼워도 잡는다")
    void catchesEvasion(String text) {
        assertThat(matcher.findMatch("제목", text)).isPresent();
    }

    @Test
    @DisplayName("걸린 금칙어는 등록한 원래 표기로 돌려준다")
    void returnsOriginalWord() {
        assertThat(matcher.findMatch("토 토 사 이 트 모음")).contains("토토사이트");
    }

    @Test
    @DisplayName("영문 금칙어는 대소문자와 전각 문자를 구분하지 않는다")
    void caseAndWidthInsensitive() {
        BannedWordMatcher english =
                new BannedWordMatcher(
                        List.of(
                                new BannedWordMatcher.Entry(
                                        "Casino", BannedWordMatcher.normalize("Casino"))));

        assertThat(english.findMatch("best CASINO here")).contains("Casino");
        assertThat(english.findMatch("ｃａｓｉｎｏ")).contains("Casino");
        assertThat(english.findMatch("c.a.s.i.n.o")).contains("Casino");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "하이원 카지노 호텔에서 자고 첫 리프트 탔어요",
                "토토로 보고 곤지암 야간 갔다 옴",
                "카풀비 먹튀 당했어요 조심하세요",
                "시즌방 보증금 먹튀 후기",
                "슬로프 사이드 슬립 연습 중",
                "바인딩 각도 카빙 질문",
                "홀 덤프 트럭 지나감"
            })
    @DisplayName("초기 목록은 스키 대화에 흔한 말(하이원 카지노, 토토로, 카풀 먹튀)을 잡지 않는다")
    void noFalsePositiveOnSkiTalk(String text) {
        assertThat(matcher.findMatch(text)).isEmpty();
    }

    @Test
    @DisplayName("목록이 비었거나 글이 비면 걸리지 않는다")
    void emptyCases() {
        assertThat(BannedWordMatcher.empty().findMatch("카지노사이트")).isEmpty();
        assertThat(matcher.findMatch((String) null, "")).isEmpty();
    }

    @Test
    @DisplayName("정리 규칙: 태그 제거, 소문자, 글자와 숫자만")
    void normalize() {
        assertThat(BannedWordMatcher.normalize("<b>AB</b> c-1!")).isEqualTo("abc1");
    }

    @Test
    @DisplayName("068 마이그레이션 금칙어: 저장한 정리 값이 normalize와 같고, 업체 광고의 여러 표기를 잡는다 (IKA-122)")
    void casinoVendorWords() {
        List<String> words = List.of("누스타", "세부카지노", "로얄탐", "tam999");
        words.forEach(word -> assertThat(BannedWordMatcher.normalize(word)).isEqualTo(word));
        BannedWordMatcher matcher =
                new BannedWordMatcher(
                        words.stream().map(w -> new BannedWordMatcher.Entry(w, w)).toList());

        assertThat(matcher.findMatch("세부 누스타 카지노, 호텔 무료 객실")).contains("누스타");
        assertThat(matcher.findMatch("#누스타호텔무료객실")).contains("누스타");
        assertThat(matcher.findMatch("#세부카지노여행")).contains("세부카지노");
        assertThat(matcher.findMatch("로얄 탐에서 상담")).contains("로얄탐");
        assertThat(matcher.findMatch("텔레그램: @Rtam999")).contains("tam999");
        assertThat(matcher.findMatch("오늘 하이원 설질 좋음")).isEmpty();
    }
}
