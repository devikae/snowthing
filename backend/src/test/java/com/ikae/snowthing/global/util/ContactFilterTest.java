package com.ikae.snowthing.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContactFilterTest {

    @Test
    @DisplayName("URL, 메신저 아이디, 국내 전화번호를 찾는다")
    void detectsContacts() {
        assertThat(ContactFilter.containsLinkOrMessenger("여기 https://example.com 봐")).isTrue();
        assertThat(ContactFilter.containsLinkOrMessenger("텔레 t.me/someone")).isTrue();
        assertThat(ContactFilter.containsLinkOrMessenger("인스타 @board_lover")).isTrue();
        assertThat(ContactFilter.containsPhone("연락 010-1234-5678")).isTrue();
        assertThat(ContactFilter.containsAnyContact("+82 10 1234 5678")).isTrue();
    }

    @Test
    @DisplayName("평범한 보드 이야기는 걸리지 않는다")
    void allowsPlainText() {
        assertThat(ContactFilter.containsAnyContact("오늘 곤지암 설질 좋음. 바인딩 각도 +15/-15")).isFalse();
    }

    @Test
    @DisplayName("글과 댓글: 광고에 쓰는 메신저 표기, 링크, 전화번호를 찾는다 (IKA-122)")
    void findsPostingContacts() {
        assertThat(ContactFilter.findPostingContact("카카오톡: TAM999")).contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("텔레그램: @Rtam999")).contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("카톡 아이디는 tam999 입니다")).contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("<p>카톡ID <b>abcd12</b></p>"))
                .contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("문의 @casino_vip")).contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("상담 t.me/someone")).contains("메신저 링크");
        assertThat(ContactFilter.findPostingContact("open.kakao.com/o/abc 로")).contains("메신저 링크");
        assertThat(ContactFilter.findPostingContact("연락 010-1234-5678")).contains("전화번호");
        // 점이 든 아이디의 앞부분을 주소로 지워 놓치지 않는다.
        assertThat(ContactFilter.findPostingContact("카톡: rider.id_123")).contains("메신저 ID");
        assertThat(ContactFilter.findPostingContact("Telegram: someuser")).contains("메신저 ID");
    }

    @Test
    @DisplayName("글과 댓글: 주소, 사진, 이메일, 일상 표현은 연락처로 보지 않는다 (IKA-122)")
    void allowsOrdinaryPosting() {
        assertThat(ContactFilter.findPostingContact("곤지암 공지 https://www.konjiamresort.co.kr 참고"))
                .isEmpty();
        assertThat(ContactFilter.findPostingContact("영상 https://youtube.com/@snowboarder 보세요"))
                .isEmpty();
        assertThat(
                        ContactFilter.findPostingContact(
                                "![사진](https://cdn.snowthing.com/post/a01012345678f.webp)"))
                .isEmpty();
        assertThat(ContactFilter.findPostingContact("문의는 help@snowthing.com 으로")).isEmpty();
        assertThat(ContactFilter.findPostingContact("리프트 라인 100m 줄 섰음")).isEmpty();
        assertThat(ContactFilter.findPostingContact("카톡 ok 하면 갈게요")).isEmpty();
        // 아이디 없이 메신저 이름만 나오는 질문은 연락처가 아니다. 라이브톡은 그대로 막는다.
        assertThat(ContactFilter.findPostingContact("Telegram 알림 설정 방법 알려 주세요")).isEmpty();
        assertThat(ContactFilter.containsLinkOrMessenger("Telegram 알림 설정 방법 알려 주세요")).isTrue();
        assertThat(ContactFilter.findPostingContact("오늘 설질 좋음. 바인딩 +15/-15")).isEmpty();
    }
}
