package com.ikae.snowthing.domain.resortweather.source;

import java.net.URI;
import java.time.Instant;
import java.util.function.BiFunction;

/** 고정 URL 하나를 받아 리조트별 파서로 읽는 공식 사이트 소스. URL은 요청 값으로 만들지 않는다. */
public class OfficialWeatherSource implements ResortWeatherSource {

    private final WeatherHttpClient httpClient;
    private final URI uri;
    private final BiFunction<byte[], Instant, WeatherReading> parser;

    public OfficialWeatherSource(
            WeatherHttpClient httpClient,
            String uri,
            BiFunction<byte[], Instant, WeatherReading> parser) {
        this.httpClient = httpClient;
        this.uri = URI.create(uri);
        this.parser = parser;
    }

    @Override
    public WeatherSourceType type() {
        return WeatherSourceType.OFFICIAL;
    }

    @Override
    public WeatherReading fetch(Instant now) {
        return parser.apply(httpClient.get(uri), now);
    }
}
