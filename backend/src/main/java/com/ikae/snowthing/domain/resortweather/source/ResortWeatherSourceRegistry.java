package com.ikae.snowthing.domain.resortweather.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.resortweather.config.ResortWeatherProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * 리조트 코드별 소스 순서. 공식 사이트가 있으면 먼저 쓰고, 기상청 초단기실황으로 대체한다. 조사 근거는
 * docs/conception/019_resort_weather_dday/01_design.md.
 */
@Component
public class ResortWeatherSourceRegistry {

    /** 리조트 좌표. DataInitializer 리조트 마스터와 같은 값이다. 기상청 5km 격자로 바꿔 쓴다. */
    private static final Map<String, double[]> BASE_COORDINATES =
            Map.ofEntries(
                    Map.entry("PHOENIX", new double[] {37.5805715, 128.3224144}),
                    Map.entry("VIVALDI", new double[] {37.6450833, 127.6820210}),
                    Map.entry("HIGH1", new double[] {37.2040383, 128.8388351}),
                    Map.entry("YONGPYONG", new double[] {37.6457553, 128.6805217}),
                    Map.entry("WELLI_HILLI", new double[] {37.4855523, 128.2477908}),
                    Map.entry("JISAN", new double[] {37.2167759, 127.3451839}),
                    Map.entry("KONJIAM", new double[] {37.3369199, 127.2935199}),
                    Map.entry("MUJU", new double[] {35.8909032, 127.7368635}),
                    Map.entry("EDEN_VALLEY", new double[] {35.4248266, 128.9852354}),
                    Map.entry("ELYSIAN", new double[] {37.8211533, 127.5889848}),
                    Map.entry("ALPENSIA", new double[] {37.6563744, 128.6733956}),
                    Map.entry("OAK_VALLEY", new double[] {37.4031965, 127.8170565}),
                    Map.entry("O2_RESORT", new double[] {37.1775331, 128.9480017}));

    /**
     * 공식 사이트에서 서버가 직접 읽을 수 있는 곳만 둔다. 에덴밸리 공식 JSON은 실황이 아닌 예보값이다. 휘닉스·오크밸리는 브라우저에서 기상청을 호출해 HTML에 값이
     * 없다. 알펜시아는 인증서 루트(OISTE WISeKey Global Root GB CA)가 JDK 신뢰 저장소에 없어 HTTPS 연결이 실패한다. 검증을 끄거나 신뢰
     * 저장소를 바꾸지 않고 기상청을 쓴다.
     */
    private static final Map<String, OfficialPage> OFFICIAL_PAGES =
            Map.of(
                    "HIGH1",
                            new OfficialPage(
                                    "https://bn.weatheri.co.kr/kangwonland/weather3.php",
                                    OfficialWeatherParsers::weatheriXml),
                    "YONGPYONG",
                            new OfficialPage(
                                    "https://hosts.weatheri.co.kr/yongpyong/weatherInfo_if.html",
                                    OfficialWeatherParsers::yongpyongWeatheri),
                    "MUJU",
                            new OfficialPage(
                                    "https://hosts.weatheri.co.kr/muju/weather20_1.php",
                                    OfficialWeatherParsers::mujuWeatheri),
                    "WELLI_HILLI",
                            new OfficialPage(
                                    "https://www.wellihillipark.com/",
                                    OfficialWeatherParsers::welliHilliNuxt),
                    "KONJIAM",
                            new OfficialPage(
                                    "https://www.konjiamresort.co.kr/main.dev",
                                    OfficialWeatherParsers::konjiam),
                    "O2_RESORT",
                            new OfficialPage(
                                    "https://www.o2resort.com/main.xhtml",
                                    OfficialWeatherParsers::o2Resort));

    private final Map<String, List<ResortWeatherSource>> sources;

    public ResortWeatherSourceRegistry(
            WeatherHttpClient httpClient,
            ObjectMapper objectMapper,
            ResortWeatherProperties properties) {
        // 키가 없으면 기상청 소스를 아예 넣지 않는다. 기상청만 쓰는 리조트는 호출 없이 204가 된다.
        String kmaServiceKey = properties.kmaServiceKey();
        boolean kmaEnabled = kmaServiceKey != null && !kmaServiceKey.isBlank();
        Map<String, List<ResortWeatherSource>> chains = new HashMap<>();
        BASE_COORDINATES.forEach(
                (resortCode, latLon) -> {
                    List<ResortWeatherSource> chain = new ArrayList<>();
                    OfficialPage page = OFFICIAL_PAGES.get(resortCode);
                    if (page != null) {
                        chain.add(new OfficialWeatherSource(httpClient, page.uri(), page.parser()));
                    }
                    if (kmaEnabled) {
                        chain.add(
                                new KmaNowcastSource(
                                        httpClient,
                                        objectMapper,
                                        kmaServiceKey,
                                        KmaGrid.fromLatLon(latLon[0], latLon[1])));
                    }
                    chains.put(resortCode, List.copyOf(chain));
                });
        this.sources = Map.copyOf(chains);
    }

    /** 등록되지 않은 리조트는 빈 목록이다. */
    public List<ResortWeatherSource> sourcesFor(String resortCode) {
        return sources.getOrDefault(resortCode, List.of());
    }

    private record OfficialPage(String uri, BiFunction<byte[], Instant, WeatherReading> parser) {}
}
