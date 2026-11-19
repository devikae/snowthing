package com.ikae.snowthing.global.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** IP 대역 비교 (IKA-96). 실제 광고 글 두 건이 같은 /64에서 호스트 부분만 바꿔 들어온 경우를 기준으로 본다. */
class IpRangeTest {

    @Test
    @DisplayName("IPv6 /64는 호스트 부분이 다른 주소를 모두 포함하고 옆 대역은 포함하지 않는다")
    void ipv6Slash64() {
        IpRange range = IpRange.parse("2001:fd8:c11e:2e00::/64").orElseThrow();

        assertThat(range.contains("2001:fd8:c11e:2e00:1a2b:3c4d:5e6f:7a8b")).isTrue();
        assertThat(range.contains("2001:0fd8:c11e:2e00:ffff:ffff:ffff:ffff")).isTrue();
        assertThat(range.contains("2001:fd8:c11e:2e01::1")).isFalse();
        assertThat(range.contains("203.0.113.5")).isFalse();
    }

    @Test
    @DisplayName("대역을 적을 때 호스트 비트를 지우고 0 묶음을 줄여 같은 대역을 같은 값으로 만든다")
    void canonicalForm() {
        assertThat(IpRange.parse("2001:fd8:c11e:2e00:abcd::5/64").orElseThrow().toCanonicalString())
                .isEqualTo("2001:fd8:c11e:2e00::/64");
        assertThat(IpRange.parse("203.0.113.77/24").orElseThrow().toCanonicalString())
                .isEqualTo("203.0.113.0/24");
        assertThat(IpRange.parse(" 203.0.113.77 ").orElseThrow().toCanonicalString())
                .isEqualTo("203.0.113.77");
        assertThat(IpRange.parse("2001:db8:0:0:1:0:0:1").orElseThrow().toCanonicalString())
                .isEqualTo("2001:db8::1:0:0:1");
    }

    @Test
    @DisplayName("IPv4 단일 주소와 /24 대역")
    void ipv4() {
        IpRange single = IpRange.parse("203.0.113.5").orElseThrow();
        IpRange block = IpRange.parse("203.0.113.0/24").orElseThrow();

        assertThat(single.contains("203.0.113.5")).isTrue();
        assertThat(single.contains("203.0.113.6")).isFalse();
        assertThat(block.contains("203.0.113.250")).isTrue();
        assertThat(block.contains("203.0.114.1")).isFalse();
        assertThat(block.contains("::ffff:203.0.113.9")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "localhost",
                "example.com",
                "203.0.113.256",
                "203.0.113.0/33",
                "2001:db8::/129",
                "203.0.113.0/",
                "203.0.113.0/-1",
                "1.2.3",
                "2001:db8::zz"
            })
    @DisplayName("숫자 IP가 아니거나 길이가 틀린 값은 읽지 않는다 (호스트 이름을 DNS로 찾지 않는다)")
    void rejectsInvalid(String value) {
        assertThat(IpRange.parse(value)).isEmpty();
    }

    @Test
    @DisplayName("요청 IP가 IP 형식이 아니면 어떤 대역에도 들지 않는다")
    void nonIpRequestIsNeverContained() {
        IpRange range = IpRange.parse("2001:db8::/32").orElseThrow();

        assertThat(range.contains((String) null)).isFalse();
        assertThat(range.contains("unknown")).isFalse();
    }
}
