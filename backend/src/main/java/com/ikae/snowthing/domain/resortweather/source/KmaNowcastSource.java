package com.ikae.snowthing.domain.resortweather.source;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기상청 단기예보 조회서비스의 초단기실황(getUltraSrtNcst). 정시 관측을 발표하므로 정시 직후(15분 전)에는 아직 발표되지 않은 것으로 보고 직전 시각을
 * 요청한다. 초단기실황에는 하늘 상태(SKY)가 없어 강수 형태(PTY)가 있을 때만 sky를 채운다.
 */
public class KmaNowcastSource implements ResortWeatherSource {

    static final String ENDPOINT =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";

    private static final int PUBLISH_DELAY_MINUTES = 15;
    private static final DateTimeFormatter BASE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASE_TIME = DateTimeFormatter.ofPattern("HHmm");
    private static final Map<String, String> PRECIPITATION_TYPES =
            Map.of(
                    "1", "비",
                    "2", "비/눈",
                    "3", "눈",
                    "5", "빗방울",
                    "6", "빗방울눈날림",
                    "7", "눈날림");

    private final WeatherHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serviceKey;
    private final KmaGrid grid;

    public KmaNowcastSource(
            WeatherHttpClient httpClient,
            ObjectMapper objectMapper,
            String serviceKey,
            KmaGrid grid) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
        this.grid = grid;
    }

    @Override
    public WeatherSourceType type() {
        return WeatherSourceType.KMA;
    }

    @Override
    public WeatherReading fetch(Instant now) {
        return parse(httpClient.get(uri(serviceKey, baseTime(now))));
    }

    static LocalDateTime baseTime(Instant now) {
        LocalDateTime nowKst = LocalDateTime.ofInstant(now, OfficialWeatherParsers.KST);
        LocalDateTime hour = nowKst.withMinute(0).withSecond(0).withNano(0);
        return nowKst.getMinute() < PUBLISH_DELAY_MINUTES ? hour.minusHours(1) : hour;
    }

    URI uri(String key, LocalDateTime base) {
        // 공공데이터포털 Decoding 키를 받는다. '+'·'/'가 들어 있어 직접 인코딩해야 서버가 원래 키로 읽는다.
        return URI.create(
                ENDPOINT
                        + "?serviceKey="
                        + URLEncoder.encode(key, StandardCharsets.UTF_8)
                        + "&pageNo=1&numOfRows=10&dataType=JSON"
                        + "&base_date="
                        + base.format(BASE_DATE)
                        + "&base_time="
                        + base.format(BASE_TIME)
                        + "&nx="
                        + grid.nx()
                        + "&ny="
                        + grid.ny());
    }

    WeatherReading parse(byte[] body) {
        JsonNode response;
        try {
            response = objectMapper.readTree(body).path("response");
        } catch (RuntimeException exception) {
            // 키 오류 등은 dataType과 무관하게 XML로 온다.
            throw new WeatherFetchException("기상청 응답이 JSON이 아닙니다.");
        }
        String resultCode = response.path("header").path("resultCode").asText("");
        if (!"00".equals(resultCode)) {
            throw new WeatherFetchException("기상청 결과 코드 " + resultCode);
        }
        BigDecimal temperature = null;
        String sky = null;
        String baseDate = null;
        String baseTime = null;
        for (JsonNode item : response.path("body").path("items").path("item")) {
            String value = item.path("obsrValue").asText("");
            switch (item.path("category").asText("")) {
                case "T1H" -> temperature = OfficialWeatherParsers.temperature(value);
                case "PTY" -> sky = PRECIPITATION_TYPES.get(value);
                default -> {}
            }
            baseDate = item.path("baseDate").asText(baseDate);
            baseTime = item.path("baseTime").asText(baseTime);
        }
        if (temperature == null || baseDate == null || baseTime == null) {
            throw new WeatherFetchException("기상청 응답에 기온이 없습니다.");
        }
        OffsetDateTime observedAt =
                LocalDateTime.of(
                                LocalDate.parse(baseDate, BASE_DATE),
                                LocalTime.parse(baseTime, BASE_TIME))
                        .atZone(OfficialWeatherParsers.KST)
                        .toOffsetDateTime();
        return new WeatherReading(temperature, sky, observedAt, WeatherSourceType.KMA);
    }
}
