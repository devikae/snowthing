package com.ikae.snowthing.domain.resortweather.source;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * 리조트 공식 사이트 응답에서 현재 기온을 뽑는다. 원본 마크업은 2026-11-04에 받은 응답 기준이며, 바뀌면 {@link WeatherFetchException}으로
 * 실패해 기상청으로 대체된다.
 */
public final class OfficialWeatherParsers {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern OBSERVED_MONTH_DAY_TIME =
            Pattern.compile("관측시간\\s*:?\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern O2_QUERIED_AT =
            Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*조회");
    private static final Pattern WELLI_NUXT_WEATHER =
            Pattern.compile("weather:\\{status:\"([^\"]*)\"[^}]*?TEMP:\"(-?\\d+(?:\\.\\d+)?)\"");
    private static final DateTimeFormatter WEATHERI_NOW =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal MIN_TEMPERATURE = new BigDecimal("-40");
    private static final BigDecimal MAX_TEMPERATURE = new BigDecimal("45");

    private OfficialWeatherParsers() {}

    /** 하이원: 웨더아이 XML {@code <data><now/><weather/><temp/>} */
    public static WeatherReading weatheriXml(byte[] body, Instant now) {
        Document document = Jsoup.parse(text(body), "", Parser.xmlParser());
        BigDecimal temperature = temperature(textOf(document.selectFirst("data > temp")));
        OffsetDateTime observedAt = fallbackTime(now);
        String rawNow = textOf(document.selectFirst("data > now"));
        if (!rawNow.isEmpty()) {
            observedAt = LocalDateTime.parse(rawNow, WEATHERI_NOW).atZone(KST).toOffsetDateTime();
        }
        return official(temperature, textOf(document.selectFirst("data > weather")), observedAt);
    }

    /** 모나용평: 웨더아이 iframe의 "모나용평 현재 날씨" 블록 */
    public static WeatherReading yongpyongWeatheri(byte[] body, Instant now) {
        Document document = Jsoup.parse(text(body));
        BigDecimal temperature =
                temperature(textOf(document.selectFirst(".weatherArea .today strong.temp span")));
        String sky = textOf(document.selectFirst(".weatherArea .today .nowWt"));
        return official(temperature, sky, observedMonthDay(document.text(), now));
    }

    /** 무주덕유산: 웨더아이 "인근지역 현재기상" 표의 무주 행 */
    public static WeatherReading mujuWeatheri(byte[] body, Instant now) {
        Document document = Jsoup.parse(text(body));
        for (Element row : document.select("table.mTable2 tr")) {
            Element header = row.selectFirst("th");
            if (header != null && "무주".equals(header.text().trim())) {
                BigDecimal temperature = temperature(textOf(row.selectFirst("td")));
                return official(temperature, null, observedMonthDay(document.text(), now));
            }
        }
        throw new WeatherFetchException("무주 관측 행이 없습니다.");
    }

    /** 웰리힐리: 서버 렌더 HTML 안 {@code window.__NUXT__}의 {@code weather} 객체 */
    public static WeatherReading welliHilliNuxt(byte[] body, Instant now) {
        Matcher matcher = WELLI_NUXT_WEATHER.matcher(text(body));
        if (!matcher.find()) {
            throw new WeatherFetchException("웰리힐리 weather 상태가 없습니다.");
        }
        return official(temperature(matcher.group(2)), matcher.group(1), fallbackTime(now));
    }

    /** 곤지암: 메인 페이지의 숨겨진 날씨 블록 */
    public static WeatherReading konjiam(byte[] body, Instant now) {
        Document document = Jsoup.parse(text(body));
        BigDecimal temperature =
                temperature(textOf(document.selectFirst("#weather dd.temperature span")));
        String sky = textOf(document.selectFirst("#weather dd.weather"));
        return official(temperature, sky, fallbackTime(now));
    }

    /** 오투: 메인 페이지의 태백 날씨 위젯(기상청 관측값 보정) */
    public static WeatherReading o2Resort(byte[] body, Instant now) {
        Document document = Jsoup.parse(text(body));
        Element temperatureElement = document.selectFirst(".o2-weather-temp");
        BigDecimal temperature =
                temperature(temperatureElement == null ? "" : temperatureElement.ownText());
        String sky = textOf(document.selectFirst(".o2-weather-sky span"));
        Matcher matcher = O2_QUERIED_AT.matcher(textOf(document.selectFirst(".o2-weather")));
        OffsetDateTime observedAt = matcher.find() ? monthDayTime(matcher, now) : fallbackTime(now);
        return official(temperature, sky, observedAt);
    }

    static BigDecimal temperature(String raw) {
        Matcher matcher = NUMBER.matcher(raw == null ? "" : raw);
        if (!matcher.find()) {
            throw new WeatherFetchException("기온 값이 없습니다.");
        }
        BigDecimal value = new BigDecimal(matcher.group());
        if (value.compareTo(MIN_TEMPERATURE) < 0 || value.compareTo(MAX_TEMPERATURE) > 0) {
            throw new WeatherFetchException("기온 값이 범위를 벗어났습니다.");
        }
        return value;
    }

    private static WeatherReading official(
            BigDecimal temperature, String sky, OffsetDateTime observedAt) {
        return new WeatherReading(
                temperature, normalizeSky(sky), observedAt, WeatherSourceType.OFFICIAL);
    }

    /** 모나용평은 "흐 림"처럼 글자 사이에 공백을 넣어 보낸다. 모든 토막이 한 글자일 때만 붙이고 "구름 조금"은 그대로 둔다. */
    static String normalizeSky(String sky) {
        if (sky == null || sky.isBlank()) {
            return null;
        }
        String[] tokens = sky.trim().split("\\s+");
        for (String token : tokens) {
            if (token.length() > 1) {
                return String.join(" ", tokens);
            }
        }
        return String.join("", tokens);
    }

    private static OffsetDateTime observedMonthDay(String pageText, Instant now) {
        Matcher matcher = OBSERVED_MONTH_DAY_TIME.matcher(pageText);
        return matcher.find() ? monthDayTime(matcher, now) : fallbackTime(now);
    }

    /** 연도가 없는 "MM/dd HH:mm"에 현재 연도를 붙인다. 연말에 받은 1월 값처럼 미래로 하루 넘게 튀면 작년으로 본다. */
    private static OffsetDateTime monthDayTime(Matcher matcher, Instant now) {
        LocalDateTime nowKst = LocalDateTime.ofInstant(now, KST);
        LocalDateTime value =
                LocalDateTime.of(
                        nowKst.getYear(),
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)));
        if (value.isAfter(nowKst.plusDays(1))) {
            value = value.minusYears(1);
        }
        return value.atZone(KST).toOffsetDateTime();
    }

    /** 원본에 시각이 없으면 수집 시각을 분 단위로 쓴다. */
    private static OffsetDateTime fallbackTime(Instant now) {
        return now.atZone(KST).truncatedTo(ChronoUnit.MINUTES).toOffsetDateTime();
    }

    private static String text(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String textOf(Element element) {
        return element == null ? "" : element.text().trim();
    }
}
