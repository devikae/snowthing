package com.ikae.snowthing.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResortVisibilityTest {

    @ParameterizedTest(name = "{0}, 활성={1} -> 커뮤니티 {2}, 슬로프 제원 {3}")
    @CsvSource({
        "KR, true, true, true",
        "KR, false, false, false",
        "JP, true, false, true",
        "JP, false, false, true",
    })
    void communityUsesActiveDomesticOnlyAndSlopeSpecShowsOverseasAlways(
            ResortCountry country,
            boolean active,
            boolean serviceTarget,
            boolean slopeSpecVisible) {
        Resort resort =
                Resort.builder()
                        .name("테스트")
                        .regionName("테스트")
                        .code("TEST")
                        .active(active)
                        .country(country)
                        .build();

        assertThat(resort.isServiceTarget()).isEqualTo(serviceTarget);
        assertThat(resort.isSlopeSpecVisible()).isEqualTo(slopeSpecVisible);
    }

    @ParameterizedTest(name = "나라를 비우면 국내로 본다")
    @CsvSource({"true"})
    void defaultsToKorea(boolean active) {
        Resort resort =
                Resort.builder().name("테스트").regionName("테스트").code("TEST").active(active).build();

        assertThat(resort.getCountry()).isEqualTo(ResortCountry.KR);
    }
}
