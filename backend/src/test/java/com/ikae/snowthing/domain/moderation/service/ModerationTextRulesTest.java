package com.ikae.snowthing.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;

/** 1단계 글 내용 규칙 (IKA-80). 패턴마다 잡는 표기와, 스키 커뮤니티 정상 글이 걸리지 않는지 본다. */
class ModerationTextRulesTest {

    private static ModerationTextRules.Findings post(String body) {
        return ModerationTextRules.inspect(ModerationTargetType.POST, "제목", body);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "문의는 t.me/snowdeal 로",
                "텔레그램 @abc123 연락",
                "텔레 아이디 abc123",
                "텔레: abc123",
                "TELEGRAM 으로 주세요",
                "<a href=\"https://t.me/abc\">여기</a>"
            })
    @DisplayName("텔레그램 연락 유도를 잡는다")
    void telegram(String body) {
        assertThat(post(body).contacts()).contains(ModerationTextRules.CONTACT_TELEGRAM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://open.kakao.com/o/sAbCdEf 들어오세요", "오픈채팅 open.kakao.com/o/gXyZ"})
    @DisplayName("오픈채팅 링크를 잡는다")
    void openChat(String body) {
        assertThat(post(body).contacts()).contains(ModerationTextRules.CONTACT_OPEN_CHAT);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "카톡 아이디 snow1234 추가",
                "카톡: snow_deal",
                "카카오톡 ID : vip777",
                "ㅋㅌ id abc99",
                "kakao: bettingking"
            })
    @DisplayName("카카오톡 아이디 표기를 잡는다")
    void kakaoId(String body) {
        assertThat(post(body).contacts()).contains(ModerationTextRules.CONTACT_KAKAO_ID);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "자세한 건 bit.ly/3abcd",
                "https://tinyurl.com/yc8x 가입",
                "me2.do/xYz 확인",
                "<a href=\"https://han.gl/AbC\">이벤트</a>"
            })
    @DisplayName("단축 주소를 잡는다")
    void shortUrl(String body) {
        assertThat(post(body).contacts()).contains(ModerationTextRules.CONTACT_SHORT_URL);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "어제 하이원 카지노 앞에서 셔틀 탔어요",
                "카풀비 먹튀 당했네요 조심하세요",
                "토토로 보다가 늦게 출발",
                "텔레비전에서 용평 개장 소식 봤어요",
                "카톡으로 연락 주세요",
                "카카오 아이디로 로그인이 안 돼요",
                "오픈채팅방 만들었어요 관심 있으면 댓글",
                "지도 naver.me/5xYz 여기서 만나요",
                "날씨는 https://www.weather.go.kr 참고",
                "리프트권 양도합니다. 가격 협의 가능",
                "<p>오늘 <b>곤지암</b> 설질 좋아요&nbsp;!</p>",
                "microsoft.com/abc 에서 받았어요"
            })
    @DisplayName("스키 커뮤니티 정상 글은 외부 연락 유도로 잡지 않는다")
    void normalPostsPass(String body) {
        ModerationTextRules.Findings findings = post(body);
        assertThat(findings.contacts()).isEmpty();
        assertThat(findings.political()).isFalse();
    }

    @Test
    @DisplayName("장터와 카풀은 오픈채팅 링크와 카톡 아이디를 보지 않고, 텔레그램과 단축 주소는 본다")
    void tradeContactsAllowedInMarketAndCarpool() {
        String body = "카톡 아이디 snow1234, open.kakao.com/o/abc";
        for (ModerationTargetType type :
                new ModerationTargetType[] {
                    ModerationTargetType.MARKET, ModerationTargetType.CARPOOL
                }) {
            assertThat(ModerationTextRules.inspect(type, "보드 팝니다", body).contacts()).isEmpty();
            assertThat(ModerationTextRules.inspect(type, "보드 팝니다", "t.me/abc bit.ly/x1").contacts())
                    .containsExactly(
                            ModerationTextRules.CONTACT_TELEGRAM,
                            ModerationTextRules.CONTACT_SHORT_URL);
        }
        assertThat(ModerationTextRules.inspect(ModerationTargetType.COMMENT, "", body).contacts())
                .containsExactly(
                        ModerationTextRules.CONTACT_OPEN_CHAT,
                        ModerationTextRules.CONTACT_KAKAO_ID);
    }

    @Test
    @DisplayName("외부 링크는 다른 주소만 세고 눈팅 주소와 사진 주소는 세지 않는다")
    void externalLinks() {
        String body =
                "https://a.example.com/1 https://a.example.com/1 www.b.example.org/x"
                        + " [링크](https://c.example.net/y) https://snowthing.org/posts/1"
                        + " ![](https://images.snowthing.org/post/a.jpg)";
        assertThat(post(body).externalLinkCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("눈팅 주소를 사용자 정보 자리에 넣은 다른 호스트 주소는 외부 링크로 센다")
    void ownHostUserInfoIsExternal() {
        String body =
                "https://snowthing.org:pw@a.example/1 https://snowthing.org@b.example/2"
                        + " http://www.snowthing.org:x@c.example/3 https://snowthing.org:8443/ok";
        assertThat(post(body).externalLinkCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("엔티티가 든 주소를 원문과 정리한 글에서 두 번 세지 않는다")
    void entityLinkCountedOnce() {
        String body = "https://a.example/?x=1&amp;y=2 https://b.example/?x=1&amp;y=2";
        assertThat(post(body).externalLinkCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("정치 글은 표시한다")
    void political() {
        assertThat(post("이번 대선 후보 누구 찍어요").political()).isTrue();
    }

    @Test
    @DisplayName("내용 해시는 기호, 띄어쓰기, 태그, 대소문자만 다른 복사본을 같은 값으로 본다")
    void contentHash() {
        String hash = ModerationTextRules.contentHash("무료 이벤트", "<p>T.ME/ABC 가입</p>");
        assertThat(ModerationTextRules.contentHash("무료  이벤트!", "t.me/abc  가입"))
                .isEqualTo(hash)
                .hasSize(64);
        assertThat(ModerationTextRules.contentHash("무료 이벤트", "t.me/abd 가입")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("2단계 입력은 태그를 지운 제목과 본문뿐이고 길이를 자른다")
    void apiInput() {
        assertThat(ModerationTextRules.apiInput("제목", "<p>본문&nbsp;내용</p>", 100))
                .isEqualTo("제목 본문 내용");
        assertThat(ModerationTextRules.apiInput("가", "나".repeat(50), 10)).hasSize(10);
    }
}
