package com.ikae.snowthing.domain.resortweather.source;

import java.time.Instant;

/** 리조트 하나의 기온을 읽어 오는 소스. 실패하면 {@link WeatherFetchException}을 던진다. */
public interface ResortWeatherSource {

    WeatherSourceType type();

    WeatherReading fetch(Instant now);
}
