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
}
