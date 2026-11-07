package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticalContentFilterTest {

    @ParameterizedTest
    @DisplayName("정당, 정치인, 선거, 정부, 이념, 지역, 세대, 성별 갈등을 잡는다")
    @ValueSource(
            strings = {
                "민주당 이번에 왜 그러냐",
                "국민의힘 지지율 봤음?",
                "대통령이 스키장 와야 함",
                "대 통 령 얘기 좀 하자",
                "대.통.령",
                "대​통령",
                "이재명 어떻게 생각함",
                "윤석열 때문에 경기가",
                "다음 선거 누구 뽑음",
                "투표하고 보드 타러 감",
                "정부가 리조트 세금을 깎아줘야지",
                "정권 바뀌면 달라지려나",
                "이 정부는 진짜 답이 없다",
                "국회에서 또 싸우네",
                "좌파들 하는 짓 보소",
                "우파가 문제임",
                "보수 진영은 이래서 안 됨",
                "진보 성향 사람들은",
                "세금 낭비 그만해라",
                "홍어들이 또",
                "전라도 놈들은 원래",
                "경상도 사람들은 왜 그럼",
                "틀딱들 슬로프에서 비켜",
                "꼰대 아저씨 진짜",
                "mz들은 인사를 안 함",
                "MZ 세대라 그런가",
                "페미 때문에 시끄럽네",
                "여자들은 왜 보드를 저렇게 타냐",
                "남자들이란 다 똑같음",
                "한남들 수준",
                "김치녀 또 나왔네"
            })
    void detectsPolitics(String text) {
        assertThat(PoliticalContentFilter.containsPolitics(text)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("보드 글에 흔한 말을 잘못 잡지 않는다")
    @ValueSource(
            strings = {
                "스노우파크 오늘 박스 열렸나요",
                "스노우파크가 제일 재밌음",
                "고정 부츠 쓰시는 분 계세요",
                "고정부츠 어떤가요",
                "데크 보수 맡기면 얼마 나와요",
                "베이스 보수 왁싱 같이 함",
                "사진보니까 설질 좋아 보이네",
                "해야 당연히 안 다치지",
                "해야당연히 낫지",
                "역시 위험한 건 안 하는 게 맞음",
                "역시위험하네요",
                "설경이 장관이네요",
                "국가대표 선수 영상 봤음",
                "한남동에서 출발합니다",
                "매일 베이직 턴만 연습함",
                "우선 거기부터 가보려고요",
                "대선배님들 조언 부탁드려요",
                "환불 정책 어떻게 되나요",
                "시즌권 가격 올랐네",
                "여가 부족해서 못 감",
                "여자친구랑 처음 갔어요",
                "남자 부츠 사이즈 추천 좀",
                "공약수 구하기 싫어서 보드 탐",
                "강원도 사람들은 좋겠다 가까워서",
                "광주 곤지암 가는 길 막히나요"
            })
    void ignoresSnowboardTalk(String text) {
        assertThat(PoliticalContentFilter.containsPolitics(text)).isFalse();
    }

    @Test
    @DisplayName("동음이의어는 넓게 잡는다. 잘못 걸려도 글 하나를 버릴 뿐이다")
    void homonymsAreBlockedOnPurpose() {
        assertThat(PoliticalContentFilter.containsPolitics("조국이 그리운 해외 원정")).isTrue();
    }

    @ParameterizedTest
    @DisplayName("비어 있으면 걸리지 않는다")
    @ValueSource(strings = {"", "   "})
    void blankIsClean(String text) {
        assertThat(PoliticalContentFilter.containsPolitics(text)).isFalse();
    }
}
