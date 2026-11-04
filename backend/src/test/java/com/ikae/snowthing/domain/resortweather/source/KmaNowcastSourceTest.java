package com.ikae.snowthing.domain.resortweather.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class KmaNowcastSourceTest {

    private final KmaNowcastSource source =
            new KmaNowcastSource(null, new ObjectMapper(), "a+b/c==", new KmaGrid(92, 120));

    @Test
    @DisplayName("기상청 격자 변환: 서울시청 좌표는 (60, 127)")
    void gridConversion() {
        assertThat(KmaGrid.fromLatLon(37.5665, 126.9780)).isEqualTo(new KmaGrid(60, 127));
    }

    @Test
    @DisplayName("정시 15분 전에는 아직 발표 전이라 직전 정시를 요청한다")
    void baseTimeWaitsForPublication() {
        assertThat(KmaNowcastSource.baseTime(kst("2026-11-04T10:14:59")))
                .isEqualTo(LocalDateTime.parse("2026-11-04T09:00"));
        assertThat(KmaNowcastSource.baseTime(kst("2026-11-04T10:15:00")))
                .isEqualTo(LocalDateTime.parse("2026-11-04T10:00"));
        assertThat(KmaNowcastSource.baseTime(kst("2026-11-05T00:05:00")))
                .isEqualTo(LocalDateTime.parse("2026-11-04T23:00"));
    }

    @Test
    @DisplayName("Decoding 키의 +와 /를 인코딩해 원래 키가 전달되게 한다")
    void encodesServiceKey() {
        String uri = source.uri("a+b/c==", LocalDateTime.parse("2026-11-04T18:00")).toString();

        assertThat(uri)
                .startsWith(KmaNowcastSource.ENDPOINT)
                .contains("serviceKey=a%2Bb%2Fc%3D%3D")
                .contains("base_date=20261104&base_time=1800")
                .contains("nx=92&ny=120")
                .contains("dataType=JSON");
    }

    @Test
    @DisplayName("T1H를 기온으로, PTY 눈을 하늘 상태로 읽는다")
    void parsesNowcast() {
        WeatherReading reading = source.parse(bytes(response("00", "-3.2", "3")));

        assertThat(reading.temperature()).isEqualByComparingTo("-3.2");
        assertThat(reading.sky()).isEqualTo("눈");
        assertThat(reading.observedAt()).isEqualTo("2026-11-04T18:00+09:00");
        assertThat(reading.source()).isEqualTo(WeatherSourceType.KMA);
    }

    @Test
    @DisplayName("강수가 없으면(PTY 0) 하늘 상태를 비운다")
    void noPrecipitationLeavesSkyEmpty() {
        assertThat(source.parse(bytes(response("00", "4.1", "0"))).sky()).isNull();
    }

    @Test
    @DisplayName("결과 코드 오류나 XML 오류 응답은 실패로 처리한다")
    void failsOnErrorResponses() {
        assertThatThrownBy(() -> source.parse(bytes(response("03", "1.0", "0"))))
                .isInstanceOf(WeatherFetchException.class);
        assertThatThrownBy(
                        () ->
                                source.parse(
                                        bytes(
                                                "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>")))
                .isInstanceOf(WeatherFetchException.class);
    }

    /** 활용가이드의 초단기실황 응답 형식을 따른 합성 응답. 실제 키로 받은 응답이 아니다. */
    private static String response(String resultCode, String temperature, String pty) {
        return """
                {"response":{"header":{"resultCode":"%s","resultMsg":"NORMAL_SERVICE"},
                "body":{"dataType":"JSON","items":{"item":[
                {"baseDate":"20261104","baseTime":"1800","category":"PTY","nx":92,"ny":120,"obsrValue":"%s"},
                {"baseDate":"20261104","baseTime":"1800","category":"REH","nx":92,"ny":120,"obsrValue":"61"},
                {"baseDate":"20261104","baseTime":"1800","category":"T1H","nx":92,"ny":120,"obsrValue":"%s"}
                ]},"pageNo":1,"numOfRows":10,"totalCount":3}}}
                """
                .formatted(resultCode, pty, temperature);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Instant kst(String localDateTime) {
        return OffsetDateTime.parse(localDateTime + "+09:00").toInstant();
    }
}
