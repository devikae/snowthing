package com.ikae.snowthing.domain.slopestatus.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 픽스처는 2026-11-09 새벽에 각 공식 사이트에서 받은 실제 응답이다(HTML은 현황 표 부분만 잘라 둠). 시즌 전이라 모두 닫혀 있다. 열린 경우는 같은 응답에서 칸
 * 글자만 OPEN으로 바꿔 확인한다. 휘닉스 시즌 중 응답은 공식 페이지 스크립트가 읽는 필드 이름으로 만든 예시다.
 */
class SlopeStatusParsersTest {

    @Test
    @DisplayName("모나용평: 난이도 rowspan을 이어 받고, 표 머리의 운영 시간을 읽는다")
    void yongpyong() throws IOException {
        SlopeSnapshot snapshot =
                SlopeStatusParsers.yongpyong(fixture("yongpyong-open-status.html"));

        assertThat(snapshot.slopes()).hasSize(21);
        assertThat(snapshot.openCount()).isZero();
        assertThat(snapshot.slopes().get(0))
                .isEqualTo(
                        new SlopeEntry(
                                "옐로우",
                                "초급",
                                SessionStatus.CLOSED,
                                SessionStatus.CLOSED,
                                null,
                                null));
        SlopeEntry megaGreen = find(snapshot, "메가그린");
        assertThat(megaGreen.difficulty()).isEqualTo("초중급");
        SlopeEntry silver = find(snapshot, "실버파라다이스");
        assertThat(silver.difficulty()).isEqualTo("중급");
        assertThat(silver.note()).isEqualTo("동계시즌 미운영");
        assertThat(find(snapshot, "드래곤파크").difficulty()).isEqualTo("익스트림");
        assertThat(snapshot.operatingNote()).isEqualTo("주간 08:30~16:30, 야간 18:00~22:00");
    }

    @Test
    @DisplayName("모나용평: OPEN 칸이 있으면 그 슬로프를 운영 중으로 센다")
    void yongpyongOpen() throws IOException {
        String html = text("yongpyong-open-status.html");
        String opened =
                html.replaceFirst(
                        "<em class=\"ico close\">CLOSE</em>", "<em class=\"ico open\">OPEN</em>");

        SlopeSnapshot snapshot = SlopeStatusParsers.yongpyong(bytes(opened));

        assertThat(snapshot.openCount()).isEqualTo(1);
        SlopeEntry yellow = find(snapshot, "옐로우");
        assertThat(yellow.day()).isEqualTo(SessionStatus.OPEN);
        assertThat(yellow.night()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    @DisplayName("곤지암: 주간, 야간, 백야 칸을 읽는다")
    void konjiam() throws IOException {
        SlopeSnapshot snapshot =
                SlopeStatusParsers.konjiam(fixture("konjiam-slope-open-close.html"));

        assertThat(snapshot.slopes()).hasSize(9);
        assertThat(snapshot.openCount()).isZero();
        assertThat(snapshot.slopes().get(0))
                .isEqualTo(
                        new SlopeEntry(
                                "그램(구 하늬)",
                                "초급",
                                SessionStatus.CLOSED,
                                SessionStatus.CLOSED,
                                SessionStatus.CLOSED,
                                null));
        assertThat(snapshot.operatingNote()).isNull();
    }

    @Test
    @DisplayName("곤지암: 대소문자가 섞인 Open도 열림으로 본다")
    void konjiamOpen() throws IOException {
        String html = text("konjiam-slope-open-close.html");
        String opened =
                html.replaceFirst("<td class=\"close\">Close</td>", "<td class=\"open\">Open</td>");

        SlopeSnapshot snapshot = SlopeStatusParsers.konjiam(bytes(opened));

        assertThat(snapshot.openCount()).isEqualTo(1);
        assertThat(snapshot.slopes().get(0).day()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    @DisplayName("하이원: rowspan 코스 이름과 번호를 합치고, 주석 처리된 줄은 건너뛴다")
    void high1() throws IOException {
        SlopeSnapshot snapshot = SlopeStatusParsers.high1(fixture("high1-slope-view.html"));

        List<String> names = snapshot.slopes().stream().map(SlopeEntry::name).toList();
        assertThat(names)
                .containsExactly(
                        "제우스 Ⅰ", "제우스 Ⅱ", "제우스 Ⅲ", "제우스 Ⅲ-1", "빅토리아 Ⅰ", "빅토리아 Ⅱ", "빅토리아 Ⅲ", "헤라 Ⅰ",
                        "헤라 Ⅱ", "헤라 Ⅲ", "아폴로 Ⅰ", "아폴로 Ⅱ", "아폴로 Ⅲ", "아폴로 Ⅳ", "아폴로 Ⅴ", "아폴로 Ⅵ",
                        "아테나 Ⅱ", "아테나 Ⅲ", "아테나 Ⅲ-1");
        assertThat(snapshot.openCount()).isZero();
        assertThat(find(snapshot, "빅토리아 Ⅲ").difficulty()).isEqualTo("최상급");
        assertThat(find(snapshot, "제우스 Ⅰ").lateNight()).isNull();
    }

    @Test
    @DisplayName("하이원: 야간 칸만 OPEN이어도 운영 중으로 센다")
    void high1NightOpen() throws IOException {
        String html = text("high1-slope-view.html");
        // 첫 줄(제우스 Ⅰ)의 주간, 야간 칸 중 야간만 연다.
        String closed = "<td><span class='slope_open close'>CLOSE</span></td>";
        int day = html.indexOf(closed);
        int night = html.indexOf(closed, day + closed.length());
        String opened =
                html.substring(0, night)
                        + "<td><span class='slope_open open'>OPEN</span></td>"
                        + html.substring(night + closed.length());

        SlopeSnapshot snapshot = SlopeStatusParsers.high1(bytes(opened));

        SlopeEntry zeus = find(snapshot, "제우스 Ⅰ");
        assertThat(zeus.day()).isEqualTo(SessionStatus.CLOSED);
        assertThat(zeus.night()).isEqualTo(SessionStatus.OPEN);
        assertThat(snapshot.openCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("하이원: 시즌에 심야 칸이 다시 열려도 난이도와 심야 상태를 제자리에서 읽는다")
    void high1LateNightColumn() {
        String html =
                """
                <table><caption>슬로프 오픈현황</caption><tbody>
                <tr><td rowspan="2">제우스<br> (Zeus)</td><td>Ⅰ</td>
                <td><span class='slope_open open'>OPEN</span></td>
                <td><span class='slope_open open'>OPEN</span></td>
                <td><span class='slope_open open'>OPEN</span></td>
                <td>초급</td><td>2,328</td></tr>
                <tr><td>Ⅱ</td>
                <td><span class='slope_open close'>CLOSE</span></td>
                <td><span class='slope_open close'>CLOSE</span></td>
                <td><span class='slope_open close'>CLOSE</span></td>
                <td>초급</td><td>2,198</td></tr>
                </tbody></table>
                """;

        SlopeSnapshot snapshot = SlopeStatusParsers.high1(bytes(html));

        assertThat(snapshot.slopes())
                .containsExactly(
                        new SlopeEntry(
                                "제우스 Ⅰ",
                                "초급",
                                SessionStatus.OPEN,
                                SessionStatus.OPEN,
                                SessionStatus.OPEN,
                                null),
                        new SlopeEntry(
                                "제우스 Ⅱ",
                                "초급",
                                SessionStatus.CLOSED,
                                SessionStatus.CLOSED,
                                SessionStatus.CLOSED,
                                null));
    }

    @Test
    @DisplayName("모나용평: 심야 칸이 추가되면 비고와 운영 시간이 밀리지 않는다")
    void yongpyongLateNightColumn() {
        String html =
                """
                <table><thead>
                <tr><th rowspan="2">난이도</th><th rowspan="2">슬로프</th><th><em class="ico week"></em>주간</th>
                <th><em class="ico nighttime"></em>야간</th><th><em class="ico midnight"></em>심야</th><th rowspan="2">비고</th></tr>
                <tr><th>08:30~16:30</th><th>18:00~22:00</th><th>22:00~24:00</th></tr>
                </thead><tbody id="slopeStatus">
                <tr><th scope="row">초급</th><td><em class="ico yellow"></em>옐로우</td>
                <td><em class="ico close">CLOSE</em></td><td><em class="ico open">OPEN</em></td>
                <td><em class="ico open">OPEN</em></td><td>야간 연장</td></tr>
                </tbody></table>
                """;

        SlopeSnapshot snapshot = SlopeStatusParsers.yongpyong(bytes(html));

        assertThat(snapshot.slopes())
                .containsExactly(
                        new SlopeEntry(
                                "옐로우",
                                "초급",
                                SessionStatus.CLOSED,
                                SessionStatus.OPEN,
                                SessionStatus.OPEN,
                                "야간 연장"));
        assertThat(snapshot.operatingNote())
                .isEqualTo("주간 08:30~16:30, 야간 18:00~22:00, 심야 22:00~24:00");
    }

    @Test
    @DisplayName("휘닉스: 시즌 밖 응답(시간대 기록 없음)은 모든 슬로프를 닫힘으로 읽는다")
    void phoenixOffSeason() throws IOException {
        SlopeSnapshot snapshot = SlopeStatusParsers.phoenix(fixture("phoenix-slope.json"));

        assertThat(snapshot.slopes()).isNotEmpty();
        assertThat(snapshot.openCount()).isZero();
        SlopeEntry sparrow = find(snapshot, "스패로우");
        assertThat(sparrow.difficulty()).isEqualTo("초급");
        assertThat(sparrow.day()).isEqualTo(SessionStatus.CLOSED);
        assertThat(sparrow.night()).isEqualTo(SessionStatus.CLOSED);
    }

    @Test
    @DisplayName("휘닉스: 주간, 야간 기록을 한 슬로프로 합치고 운영 시간을 비고에 남긴다")
    void phoenixInSeason() {
        String json =
                """
                [
                  {"slopeNm":"스패로우","dfflyNm":"초급","slopeOpenDivNm":"주간","slopeOpenTime":"08:30-16:30","slopeOpenYn":"Y"},
                  {"slopeNm":"스패로우","dfflyNm":"초급","slopeOpenDivNm":"야간","slopeOpenTime":"18:30-22:00"},
                  {"slopeNm":"펭귄","dfflyNm":"초급","slopeOpenDivNm":"주간","slopeOpenTime":"08:30-16:30","slopeOpenYn":"N"},
                  {"slopeNm":"키위","dfflyNm":"중급","slopeOpenDivNm":"야간","slopeOpenTime":"-"}
                ]
                """;

        SlopeSnapshot snapshot = SlopeStatusParsers.phoenix(bytes(json));

        assertThat(snapshot.slopes()).hasSize(3);
        assertThat(snapshot.slopes().get(0))
                .isEqualTo(
                        new SlopeEntry(
                                "스패로우",
                                "초급",
                                SessionStatus.OPEN,
                                SessionStatus.OPEN,
                                null,
                                "주간 08:30-16:30, 야간 18:30-22:00"));
        assertThat(find(snapshot, "펭귄").hasOpenSession()).isFalse();
        assertThat(find(snapshot, "키위").night()).isEqualTo(SessionStatus.CLOSED);
        assertThat(snapshot.openCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("표를 찾지 못하거나 슬로프가 하나도 없으면 실패로 처리한다")
    void structureChangedFails() {
        byte[] page = bytes("<html><body><p>점검 중</p></body></html>");

        assertThatThrownBy(() -> SlopeStatusParsers.yongpyong(page))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(() -> SlopeStatusParsers.konjiam(page))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(() -> SlopeStatusParsers.high1(page))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(
                        () ->
                                SlopeStatusParsers.yongpyong(
                                        bytes("<tbody id=\"slopeStatus\"></tbody>")))
                .isInstanceOf(SlopeFetchException.class);
    }

    @Test
    @DisplayName("휘닉스: JSON이 아니거나 빈 배열이면 실패로 처리한다")
    void phoenixBrokenFails() {
        assertThatThrownBy(() -> SlopeStatusParsers.phoenix(bytes("<html>error</html>")))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(() -> SlopeStatusParsers.phoenix(bytes("[]")))
                .isInstanceOf(SlopeFetchException.class);
        assertThatThrownBy(() -> SlopeStatusParsers.phoenix(bytes("{\"slopeNm\":\"a\"}")))
                .isInstanceOf(SlopeFetchException.class);
    }

    @Test
    @DisplayName("칸 글자 판단: OPEN만 열림, 비었거나 -면 정보 없음, 나머지는 닫힘")
    void statusText() {
        assertThat(SlopeStatusParsers.status(" open ")).isEqualTo(SessionStatus.OPEN);
        assertThat(SlopeStatusParsers.status("CLOSE")).isEqualTo(SessionStatus.CLOSED);
        assertThat(SlopeStatusParsers.status("정설중")).isEqualTo(SessionStatus.CLOSED);
        assertThat(SlopeStatusParsers.status("OPEN 예정")).isEqualTo(SessionStatus.CLOSED);
        assertThat(SlopeStatusParsers.status("")).isNull();
        assertThat(SlopeStatusParsers.status("-")).isNull();
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
