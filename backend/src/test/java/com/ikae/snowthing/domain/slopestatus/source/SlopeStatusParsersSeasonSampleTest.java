package com.ikae.snowthing.domain.slopestatus.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 시즌 중 실제 응답과 깨진 응답 (IKA-118).
 *
 * <p>{@code *-20261124.*} 픽스처는 2026-11-24 새벽에 공식 사이트에서 리조트당 한 번씩 받은 응답이다(HTML은 현황 표 부분만 잘라 둠).
 * 모나용평과 휘닉스는 개장해서 열린 슬로프가 있다. 하이원과 곤지암은 아직 개장 전이고 11-09 픽스처와 표 구조가 같아 따로 두지 않았다. 기대값은 파서가 아니라 픽스처를
 * 직접 읽어 적었다.
 *
 * <p>깨진 응답은 "열린 슬로프 0개" 같은 그럴듯한 값이 아니라 {@link SlopeFetchException}(수집 실패)이어야 한다. 실패하면 수집기가 마지막 성공
 * 값을 그대로 둔다.
 */
class SlopeStatusParsersSeasonSampleTest {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    @Nested
    @DisplayName("시즌 중 실제 응답")
    class SeasonSamples {

        @Test
        @DisplayName("모나용평: 열린 슬로프 하나와 그 비고, 주간 운영 시간만 읽는다(야간 시간 칸은 비어 있다)")
        void yongpyongInSeason() throws IOException {
            SlopeSnapshot snapshot =
                    SlopeStatusParsers.yongpyong(fixture("yongpyong-open-status-20261124.html"));

            assertThat(snapshot.slopes()).hasSize(21);
            assertThat(snapshot.openCount()).isEqualTo(1);
            assertThat(find(snapshot, "핑크"))
                    .isEqualTo(
                            new SlopeEntry(
                                    "핑크",
                                    "초중급",
                                    SessionStatus.OPEN,
                                    SessionStatus.CLOSED,
                                    null,
                                    "운영시간 08:30 ~ 16:30"));
            assertThat(find(snapshot, "옐로우").hasOpenSession()).isFalse();
            assertThat(find(snapshot, "실버파라다이스").note()).isEqualTo("동계시즌 미운영");
            assertThat(find(snapshot, "드래곤파크").difficulty()).isEqualTo("익스트림");
            assertThat(snapshot.operatingNote()).isEqualTo("주간 08:30~16:30");
        }

        @Test
        @DisplayName("휘닉스: 시즌 중 응답에서 주간 기록이 있는 슬로프만 열림이고 운영 시간이 비고에 남는다")
        void phoenixInSeason() throws IOException {
            SlopeSnapshot snapshot =
                    SlopeStatusParsers.phoenix(fixture("phoenix-slope-20261124.json"));

            assertThat(snapshot.slopes()).hasSize(17);
            assertThat(snapshot.openCount()).isEqualTo(2);
            assertThat(
                            snapshot.slopes().stream()
                                    .filter(SlopeEntry::hasOpenSession)
                                    .map(SlopeEntry::name)
                                    .toList())
                    .containsExactly("도도(강습전용)", "호크");
            assertThat(find(snapshot, "호크"))
                    .isEqualTo(
                            new SlopeEntry(
                                    "호크",
                                    "중급",
                                    SessionStatus.OPEN,
                                    SessionStatus.CLOSED,
                                    null,
                                    "주간 09:00-16:00"));
            assertThat(find(snapshot, "스패로우"))
                    .isEqualTo(
                            new SlopeEntry(
                                    "스패로우",
                                    "초급",
                                    SessionStatus.CLOSED,
                                    SessionStatus.CLOSED,
                                    null,
                                    null));
        }
    }

    @Nested
    @DisplayName("깨진 응답은 수집 실패")
    class Broken {

        @Test
        @DisplayName("빈 응답과 공백만 있는 응답")
        void emptyBody() {
            for (byte[] body : List.of(new byte[0], bytes("   \n"))) {
                assertFails(() -> SlopeStatusParsers.yongpyong(body));
                assertFails(() -> SlopeStatusParsers.konjiam(body));
                assertFails(() -> SlopeStatusParsers.high1(body));
                assertFails(() -> SlopeStatusParsers.phoenix(body));
            }
        }

        @Test
        @DisplayName("인코딩이 다른 응답(EUC-KR): 표는 읽혀도 깨진 이름을 저장하지 않는다")
        void differentEncoding() throws IOException {
            assertFails(
                    () ->
                            SlopeStatusParsers.yongpyong(
                                    recode("yongpyong-open-status-20261124.html")));
            assertFails(() -> SlopeStatusParsers.konjiam(recode("konjiam-slope-open-close.html")));
            assertFails(() -> SlopeStatusParsers.high1(recode("high1-slope-view.html")));
            assertFails(() -> SlopeStatusParsers.phoenix(recode("phoenix-slope-20261124.json")));
        }

        @Test
        @DisplayName("모나용평: 상태 아이콘이 글자 없이 클래스만 남으면 닫힘 0개가 아니라 실패다")
        void yongpyongIconWithoutText() throws IOException {
            String html =
                    text("yongpyong-open-status-20261124.html")
                            .replace(">OPEN</em>", "></em>")
                            .replace(">CLOSE</em>", "></em>");

            assertFails(() -> SlopeStatusParsers.yongpyong(bytes(html)));
        }

        @Test
        @DisplayName("모나용평: 상태 아이콘 요소가 사라지면 실패다")
        void yongpyongStatusMarkerMissing() throws IOException {
            String html =
                    text("yongpyong-open-status-20261124.html")
                            .replace("<em class=\"ico open\">OPEN</em>", "OPEN")
                            .replace("<em class=\"ico close\">CLOSE</em>", "CLOSE");

            assertFails(() -> SlopeStatusParsers.yongpyong(bytes(html)));
        }

        @Test
        @DisplayName("곤지암: 상태 칸이 모두 비면 실패다")
        void konjiamBlankStatusCells() throws IOException {
            String html =
                    text("konjiam-slope-open-close.html")
                            .replace("<td class=\"close\">Close</td>", "<td class=\"close\"></td>");

            assertFails(() -> SlopeStatusParsers.konjiam(bytes(html)));
        }

        @Test
        @DisplayName("곤지암: 칸 수가 줄어든 표(백야 칸 없음)는 한 칸씩 밀어 읽지 않고 실패다")
        void konjiamColumnRemoved() {
            String html =
                    """
                    <table class="chart-a1"><caption>슬로프 오픈 현황 - 슬로프, 수준, 주간, 야간</caption><tbody>
                    <tr><td>그램</td><td>초급</td><td class="open">Open</td><td class="close">Close</td></tr>
                    </tbody></table>
                    """;

            assertFails(() -> SlopeStatusParsers.konjiam(bytes(html)));
        }

        @Test
        @DisplayName("곤지암: 리프트 표만 남으면 슬로프로 읽지 않고 실패다")
        void konjiamOnlyLiftTable() {
            String html =
                    """
                    <table class="chart-a1 mt10"><caption>리프트 오픈 현황 - 슬로프, 하늬 리프트</caption><tbody>
                    <tr><td>하늬</td><td>초급</td><td>Open</td><td>Open</td><td>Open</td></tr>
                    </tbody></table>
                    """;

            assertFails(() -> SlopeStatusParsers.konjiam(bytes(html)));
        }

        @Test
        @DisplayName("하이원: 상태 표시(span.slope_open)가 사라지면 실패다")
        void high1StatusMarkerMissing() throws IOException {
            String html = text("high1-slope-view.html").replace("slope_open", "slope_state");

            assertFails(() -> SlopeStatusParsers.high1(bytes(html)));
        }

        @Test
        @DisplayName("하이원: 표 몸통이 비면 실패다")
        void high1EmptyBody() {
            assertFails(
                    () ->
                            SlopeStatusParsers.high1(
                                    bytes(
                                            "<table><caption>슬로프 오픈현황</caption>"
                                                    + "<tbody></tbody></table>")));
        }

        @Test
        @DisplayName("휘닉스: 오류 객체, null, 숫자 배열, 이름 없는 항목만 있는 배열은 실패다")
        void phoenixUnexpectedJson() {
            for (String json :
                    List.of(
                            "{\"status\":500,\"message\":\"error\"}",
                            "null",
                            "[1,2,3]",
                            "[{\"dfflyNm\":\"초급\"},{\"slopeNm\":\" \"}]",
                            "[{\"slopeNm\":\"스패로우\"")) {
                assertFails(() -> SlopeStatusParsers.phoenix(bytes(json)));
            }
        }
    }

    @Nested
    @DisplayName("열림을 부풀리지 않는다")
    class NarrowOpen {

        @Test
        @DisplayName("휘닉스: 운영 여부가 N이면 운영 시간이 있어도 닫힘이고, 모르는 시간대 이름은 열림으로 세지 않는다")
        void phoenixClosedWinsOverTime() {
            String json =
                    """
                    [
                      {"slopeNm":"호크","dfflyNm":"중급","slopeOpenDivNm":"주간","slopeOpenYn":"N","slopeOpenTime":"09:00-16:00"},
                      {"slopeNm":"밸리","dfflyNm":"중상급","slopeOpenDivNm":"새벽","slopeOpenYn":"Y","slopeOpenTime":"05:00-07:00"}
                    ]
                    """;

            SlopeSnapshot snapshot = SlopeStatusParsers.phoenix(bytes(json));

            assertThat(snapshot.openCount()).isZero();
            assertThat(find(snapshot, "호크").note()).isNull();
        }

        @Test
        @DisplayName("모나용평: 운영 예정, 정설 같은 글자는 닫힘이다")
        void yongpyongUnknownStatusText() throws IOException {
            String html =
                    text("yongpyong-open-status-20261124.html")
                            .replace(">OPEN</em>", ">OPEN 예정</em>");

            SlopeSnapshot snapshot = SlopeStatusParsers.yongpyong(bytes(html));

            assertThat(snapshot.openCount()).isZero();
            assertThat(find(snapshot, "핑크").day()).isEqualTo(SessionStatus.CLOSED);
        }
    }

    private static void assertFails(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(SlopeFetchException.class);
    }

    private static SlopeEntry find(SlopeSnapshot snapshot, String name) {
        return snapshot.slopes().stream()
                .filter(slope -> slope.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] recode(String name) throws IOException {
        return text(name).getBytes(EUC_KR);
    }

    private static String text(String name) throws IOException {
        return new String(fixture(name), StandardCharsets.UTF_8);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input =
                new ClassPathResource("fixtures/slopestatus/" + name).getInputStream()) {
            return input.readAllBytes();
        }
    }
}
