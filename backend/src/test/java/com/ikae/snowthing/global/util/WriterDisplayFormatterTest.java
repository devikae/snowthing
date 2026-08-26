package com.ikae.snowthing.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.ikae.snowthing.domain.member.entity.Member;

class WriterDisplayFormatterTest {

    @Test
    @DisplayName("회원 작성글은 회원의 닉네임을 반환한다")
    void format_memberWriter_returnsNickname() {
        Member member = Member.builder().nickname("눈사람보더").build();

        String result = WriterDisplayFormatter.format(false, member, "192.168.1.100");

        assertThat(result).isEqualTo("눈사람보더");
    }

    @Test
    @DisplayName("회원 정보가 null이거나 닉네임이 없으면 '알 수 없음'을 반환한다")
    void format_nullMember_returnsUnknown() {
        String result1 = WriterDisplayFormatter.format(false, null, "192.168.1.100");
        String result2 =
                WriterDisplayFormatter.format(false, Member.builder().build(), "192.168.1.100");

        assertThat(result1).isEqualTo("알 수 없음");
        assertThat(result2).isEqualTo("알 수 없음");
    }

    @ParameterizedTest
    @CsvSource({
        "192.168.0.1, 익명 (192.168.***.***)",
        "127.0.0.1, 익명 (127.0.***.***)",
        "10.20.30.40, 익명 (10.20.***.***)"
    })
    @DisplayName("익명 작성글은 IPv4를 정상 마스킹하여 '익명 (A.B.***.***)' 형식으로 반환한다")
    void format_anonymousWriter_masksIpv4(String ip, String expected) {
        String result = WriterDisplayFormatter.format(true, null, ip);

        assertThat(result).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "0:0:0:0:0:0:0:1", "::1", "invalid_ip_format", "999"})
    @DisplayName("익명 작성글의 IP가 null, 공백, 루프백, 또는 비정상 문자열일 경우 디폴트 마스킹을 반환한다")
    void format_anonymousWriter_defaultMaskedIp(String ip) {
        String result = WriterDisplayFormatter.format(true, null, ip);

        assertThat(result).isEqualTo("익명 (127.0.***.***)");
    }

    @ParameterizedTest
    @CsvSource({
        "2001:0db8:85a3:0000:0000:8a2e:0370:7334, 익명 (2001:0db8:****:****)",
        "fe80:1234:5678::1, 익명 (fe80:1234:****:****)"
    })
    @DisplayName("익명 작성글이 IPv6일 경우 앞 2개 세그먼트를 제외하고 정상 마스킹한다")
    void format_anonymousWriter_masksIpv6(String ip, String expected) {
        String result = WriterDisplayFormatter.format(true, null, ip);

        assertThat(result).isEqualTo(expected);
    }
}
