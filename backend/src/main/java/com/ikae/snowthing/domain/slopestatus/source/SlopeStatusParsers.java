package com.ikae.snowthing.domain.slopestatus.source;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 리조트 공식 현황 페이지 파서. 순수 함수라 저장해 둔 실제 응답으로 테스트한다. 조사 근거와 페이지 구조는
 * docs/conception/030_slope_status/01_design.md.
 *
 * <p>공통 규칙: 칸 글자가 정확히 OPEN이면 열림, 비었거나 "-"면 정보 없음(null), 그 밖의 글자(CLOSE, 정설 등)는 닫힘으로 본다. 출처가 새 표기를 쓰기
 * 시작해도 열린 슬로프를 잘못 세지 않도록 열림은 좁게 판단한다. 슬로프가 하나도 읽히지 않으면 구조가 바뀐 것으로 보고 실패로 처리한다.
 */
public final class SlopeStatusParsers {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private SlopeStatusParsers() {}

    /** 모나용평 /kor/skiNboard/slope/openStatusBoard.do. 난이도 칸이 rowspan으로 묶여 있다. */
    public static SlopeSnapshot yongpyong(byte[] body) {
        Document document = parse(body);
        Element tbody = document.getElementById("slopeStatus");
        if (tbody == null) {
            throw new SlopeFetchException("모나용평 슬로프 표(#slopeStatus)를 찾지 못했습니다.");
        }
        List<SlopeEntry> slopes = new ArrayList<>();
        String difficulty = null;
        for (Element row : tbody.select("> tr")) {
            Element group = row.selectFirst("> th[scope=row]");
            if (group != null) {
                difficulty = blankToNull(group.text());
            }
            Elements cells = row.select("> td");
            if (cells.isEmpty()) {
                continue;
            }
            String name = blankToNull(cells.get(0).text());
            // 이름 다음부터 상태 아이콘(em.ico)이 든 칸이 주간, 야간(, 심야) 순서로 이어지고 그 뒤가 비고다. 시즌에 심야 칸이 다시
            // 열려도 밀리지 않게 위치 대신 아이콘으로 상태 칸을 찾는다.
            StatusCells statusCells = statusCells(cells, 1, "em.ico");
            if (name == null || statusCells.statuses().isEmpty()) {
                continue;
            }
            String note =
                    statusCells.next() < cells.size()
                            ? blankToNull(cells.get(statusCells.next()).text())
                            : null;
            slopes.add(entry(name, difficulty, statusCells.statuses(), note));
        }
        return snapshot("모나용평", slopes, yongpyongHours(document));
    }

    /** 표 머리 둘째 줄에 시간대별 운영 시간이 있다. 빈 칸은 건너뛴다. */
    private static String yongpyongHours(Document document) {
        Element table = document.selectFirst("table:has(#slopeStatus)");
        if (table == null) {
            return null;
        }
        Elements rows = table.select("> thead > tr");
        if (rows.size() < 2) {
            return null;
        }
        // 첫 줄에서 rowspan이 없는 칸(주간, 야간 등)이 둘째 줄 시간 칸과 순서대로 짝이 된다.
        List<String> labels =
                rows.get(0).select("> th:not([rowspan])").stream().map(Element::text).toList();
        Elements times = rows.get(1).select("> th");
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(times.size(), labels.size()); i++) {
            String label = blankToNull(labels.get(i));
            String time = blankToNull(times.get(i).text());
            if (label != null && time != null) {
                parts.add(label + " " + time);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    /** 곤지암 /ski/slopeOpenClose.dev. 슬로프, 수준, 주간, 야간, 백야 순서다. */
    public static SlopeSnapshot konjiam(byte[] body) {
        Document document = parse(body);
        Element table = null;
        for (Element candidate : document.select("table.chart-a1")) {
            Element caption = candidate.selectFirst("> caption");
            if (caption != null && caption.text().contains("슬로프 오픈 현황")) {
                table = candidate;
                break;
            }
        }
        if (table == null) {
            throw new SlopeFetchException("곤지암 슬로프 오픈 현황 표를 찾지 못했습니다.");
        }
        List<SlopeEntry> slopes = new ArrayList<>();
        for (Element row : table.select("> tbody > tr")) {
            Elements cells = row.select("> td");
            if (cells.size() < 5) {
                continue;
            }
            String name = blankToNull(cells.get(0).text());
            if (name == null) {
                continue;
            }
            slopes.add(
                    new SlopeEntry(
                            name,
                            blankToNull(cells.get(1).text()),
                            status(cells.get(2).text()),
                            status(cells.get(3).text()),
                            status(cells.get(4).text()),
                            null));
        }
        return snapshot("곤지암", slopes, null);
    }

    /**
     * 하이원 /ski/slopeView.do?key=748&mode=p. 첫 칸이 rowspan으로 묶인 슬로프 이름(제우스 등)이고, 다음 칸이 번호(Ⅰ, Ⅲ-1)다.
     * 주석 처리된 줄과 칸이 섞여 있어 rowspan 숫자를 믿지 않고, rowspan 칸이 있는 줄에서만 이름을 바꾼다.
     */
    public static SlopeSnapshot high1(byte[] body) {
        Document document = parse(body);
        Element table = null;
        for (Element candidate : document.select("table")) {
            Element caption = candidate.selectFirst("> caption");
            if (caption != null && caption.text().contains("슬로프 오픈현황")) {
                table = candidate;
                break;
            }
        }
        if (table == null) {
            throw new SlopeFetchException("하이원 슬로프 오픈현황 표를 찾지 못했습니다.");
        }
        List<SlopeEntry> slopes = new ArrayList<>();
        String course = null;
        for (Element row : table.select("> tbody > tr")) {
            List<Element> cells = new ArrayList<>(row.select("> td"));
            if (!cells.isEmpty() && cells.get(0).hasAttr("rowspan")) {
                course = courseName(cells.remove(0).text());
            }
            if (cells.isEmpty() || course == null) {
                continue;
            }
            // 번호 다음부터 span.slope_open 칸이 주간, 야간(, 심야) 순서로 이어지고 그 뒤가 난이도다. 심야 칸이 다시 열려도 밀리지
            // 않게 표시로 찾는다.
            StatusCells statusCells = statusCells(cells, 1, "span.slope_open");
            if (statusCells.statuses().isEmpty()) {
                continue;
            }
            String number = blankToNull(cells.get(0).text());
            String name = number == null ? course : course + " " + number;
            String difficulty =
                    statusCells.next() < cells.size()
                            ? blankToNull(cells.get(statusCells.next()).text())
                            : null;
            slopes.add(entry(name, difficulty, statusCells.statuses(), null));
        }
        return snapshot("하이원", slopes, null);
    }

    /** 상태 칸 목록과 그 다음 칸 위치. */
    private record StatusCells(List<SessionStatus> statuses, int next) {}

    /** start부터 marker가 든 칸을 이어서 읽는다. 처음으로 marker가 없는 칸에서 멈춘다. */
    private static StatusCells statusCells(List<Element> cells, int start, String marker) {
        List<SessionStatus> statuses = new ArrayList<>();
        int index = start;
        while (index < cells.size() && cells.get(index).selectFirst(marker) != null) {
            statuses.add(status(cells.get(index).text()));
            index++;
        }
        return new StatusCells(statuses, index);
    }

    /** 상태 칸은 주간, 야간, 심야 순서다. 없는 칸은 null. */
    private static SlopeEntry entry(
            String name, String difficulty, List<SessionStatus> statuses, String note) {
        return new SlopeEntry(
                name,
                difficulty,
                statuses.get(0),
                statuses.size() > 1 ? statuses.get(1) : null,
                statuses.size() > 2 ? statuses.get(2) : null,
                note);
    }

    /** "제우스 (Zeus)"에서 한글 이름만 남긴다. */
    private static String courseName(String text) {
        int paren = text.indexOf('(');
        String name = paren >= 0 ? text.substring(0, paren) : text;
        return blankToNull(name);
    }

    /**
     * 휘닉스 /api/snowpark/slope JSON. 공식 페이지 스크립트와 같은 규칙으로 읽는다: 슬로프마다 주간, 야간 기록이 따로
     * 오고(slopeOpenDivNm), 운영 시간(slopeOpenTime)이 있으면 그 시간대에 연다. 시즌 밖에는 시간대 기록 없이 제원만 온다. 이때는 모든 슬로프를
     * 닫힘으로 본다(공식 페이지도 "-"로 표시한다).
     */
    public static SlopeSnapshot phoenix(byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JacksonException exception) {
            throw new SlopeFetchException("휘닉스 응답이 JSON이 아닙니다.");
        }
        if (root == null || !root.isArray()) {
            throw new SlopeFetchException("휘닉스 응답이 배열이 아닙니다.");
        }
        Map<String, PhoenixSlope> merged = new LinkedHashMap<>();
        for (JsonNode node : root) {
            String name = blankToNull(text(node, "slopeNm"));
            if (name == null) {
                continue;
            }
            PhoenixSlope slope =
                    merged.computeIfAbsent(
                            name, key -> new PhoenixSlope(key, blankToNull(text(node, "dfflyNm"))));
            String division = text(node, "slopeOpenDivNm");
            if (division == null) {
                continue;
            }
            String time = blankToNull(text(node, "slopeOpenTime"));
            if ("-".equals(time)) {
                time = null;
            }
            String openYn = text(node, "slopeOpenYn");
            boolean open =
                    openYn != null && !openYn.isBlank()
                            ? "Y".equalsIgnoreCase(openYn.trim())
                            : time != null;
            if (division.contains("주간")) {
                slope.day = open;
                slope.dayTime = open ? time : null;
            } else if (division.contains("야간")) {
                slope.night = open;
                slope.nightTime = open ? time : null;
            }
        }
        List<SlopeEntry> slopes = merged.values().stream().map(PhoenixSlope::toEntry).toList();
        return snapshot("휘닉스", slopes, null);
    }

    private static final class PhoenixSlope {
        private final String name;
        private final String difficulty;
        private boolean day;
        private boolean night;
        private String dayTime;
        private String nightTime;

        private PhoenixSlope(String name, String difficulty) {
            this.name = name;
            this.difficulty = difficulty;
        }

        private SlopeEntry toEntry() {
            List<String> times = new ArrayList<>();
            if (dayTime != null) {
                times.add("주간 " + dayTime);
            }
            if (nightTime != null) {
                times.add("야간 " + nightTime);
            }
            return new SlopeEntry(
                    name,
                    difficulty,
                    day ? SessionStatus.OPEN : SessionStatus.CLOSED,
                    night ? SessionStatus.OPEN : SessionStatus.CLOSED,
                    null,
                    times.isEmpty() ? null : String.join(", ", times));
        }
    }

    static SessionStatus status(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || "-".equals(value)) {
            return null;
        }
        return "OPEN".equals(value.toUpperCase(Locale.ROOT))
                ? SessionStatus.OPEN
                : SessionStatus.CLOSED;
    }

    private static SlopeSnapshot snapshot(
            String resortName, List<SlopeEntry> slopes, String operatingNote) {
        if (slopes.isEmpty()) {
            throw new SlopeFetchException(resortName + " 현황에서 슬로프를 하나도 읽지 못했습니다.");
        }
        return new SlopeSnapshot(slopes, operatingNote);
    }

    private static Document parse(byte[] body) {
        return Jsoup.parse(new String(body, StandardCharsets.UTF_8));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replace(' ', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
