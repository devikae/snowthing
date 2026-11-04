package com.ikae.snowthing.domain.resortweather.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.function.BiFunction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 픽스처는 2026-11-04 18시대에 각 공식 사이트에서 받은 실제 응답이다(큰 페이지는 날씨 블록만 잘라 둠). */
class OfficialWeatherParsersTest {

    private static final Instant NOW =
            OffsetDateTime.parse("2026-11-04T18:25:00+09:00").toInstant();

    @Test
    @DisplayName("하이원 웨더아이 XML에서 기온·하늘·관측 시각을 읽는다")
    void high1() throws IOException {
        WeatherReading reading =
                OfficialWeatherParsers.weatheriXml(fixture("high1-weatheri.xml"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("8.9");
        assertThat(reading.sky()).isEqualTo("맑음");
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:00+09:00");
        assertThat(reading.source()).isEqualTo(WeatherSourceType.OFFICIAL);
    }

    @Test
    @DisplayName("모나용평 웨더아이 페이지의 현재 날씨 블록을 읽는다")
    void yongpyong() throws IOException {
        WeatherReading reading =
                OfficialWeatherParsers.yongpyongWeatheri(fixture("yongpyong-weatheri.html"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("5.6");
        assertThat(reading.sky()).isEqualTo("구름많음");
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:00+09:00");
    }

    @Test
    @DisplayName("무주 웨더아이 표에서 무주 관측 행의 기온을 읽는다")
    void muju() throws IOException {
        WeatherReading reading =
                OfficialWeatherParsers.mujuWeatheri(fixture("muju-weatheri.html"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("11.6");
        assertThat(reading.sky()).isNull();
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:00+09:00");
    }

    @Test
    @DisplayName("웰리힐리 Nuxt 상태의 weather 객체를 읽고, 시각이 없으면 수집 시각을 쓴다")
    void welliHilli() throws IOException {
        WeatherReading reading =
                OfficialWeatherParsers.welliHilliNuxt(fixture("welli-nuxt.html"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("9");
        assertThat(reading.sky()).isEqualTo("흐림");
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:25+09:00");
    }

    @Test
    @DisplayName("한 글자씩 띄어 쓴 하늘 상태만 붙이고 일반 띄어쓰기는 유지한다")
    void normalizesSpacedSky() {
        assertThat(OfficialWeatherParsers.normalizeSky(" 흐 림 ")).isEqualTo("흐림");
        assertThat(OfficialWeatherParsers.normalizeSky("구름  조금")).isEqualTo("구름 조금");
        assertThat(OfficialWeatherParsers.normalizeSky(" ")).isNull();
    }

    @Test
    @DisplayName("곤지암 메인의 숨겨진 날씨 블록을 읽는다")
    void konjiam() throws IOException {
        WeatherReading reading = OfficialWeatherParsers.konjiam(fixture("konjiam.html"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("14.1");
        assertThat(reading.sky()).isEqualTo("구름 조금");
    }

    @Test
    @DisplayName("오투 메인 위젯의 보정 기온과 조회 시각을 읽는다")
    void o2Resort() throws IOException {
        WeatherReading reading = OfficialWeatherParsers.o2Resort(fixture("o2.html"), NOW);

        assertThat(reading.temperature()).isEqualByComparingTo("9.0");
        assertThat(reading.sky()).isEqualTo("맑음");
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:20+09:00");
    }

    @Test
    @DisplayName("연도 없는 관측 시각을 1월 1일에 받으면 12월 31일은 작년으로 본다")
    void monthDayWithoutYearRollsBackAcrossNewYear() {
        Instant newYear = OffsetDateTime.parse("2027-01-01T00:20:00+09:00").toInstant();
        byte[] body =
                "<table class=\"mTable2\"><tr><th>무주</th><td>-3.0</td></tr></table><h4>관측시간 : 12/31 23:00</h4>"
                        .getBytes(StandardCharsets.UTF_8);

        WeatherReading reading = OfficialWeatherParsers.mujuWeatheri(body, newYear);

        assertThat(reading.observedAt()).isEqualTo("2026-12-31T23:00+09:00");
    }

    @Test
    @DisplayName("마크업이 바뀌어 값을 못 찾거나 자리표시·이상값이면 실패한다")
    void failsWhenMarkupChanged() {
        assertFails(OfficialWeatherParsers::konjiam, "<div id=\"weather\"></div>");
        assertFails(OfficialWeatherParsers::o2Resort, "<div class=\"o2-weather-temp\">-</div>");
        assertFails(OfficialWeatherParsers::welliHilliNuxt, "<script>window.__NUXT__={}</script>");
        assertFails(OfficialWeatherParsers::mujuWeatheri, "<table class=\"mTable2\"></table>");
        assertFails(
                OfficialWeatherParsers::weatheriXml,
                "<data><now>2026-11-04 18:00:00</now><temp>99.0</temp></data>");
    }

    private void assertFails(BiFunction<byte[], Instant, WeatherReading> parser, String body) {
        assertThatThrownBy(() -> parser.apply(body.getBytes(StandardCharsets.UTF_8), NOW))
                .isInstanceOf(WeatherFetchException.class);
    }

    static byte[] fixture(String name) throws IOException {
        try (InputStream input =
                new ClassPathResource("fixtures/weather/" + name).getInputStream()) {
            return input.readAllBytes();
        }
    }
}
