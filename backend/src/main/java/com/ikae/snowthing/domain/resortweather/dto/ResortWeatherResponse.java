package com.ikae.snowthing.domain.resortweather.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.ikae.snowthing.domain.resortweather.cache.WeatherSnapshot;
import com.ikae.snowthing.domain.resortweather.source.WeatherReading;
import com.ikae.snowthing.domain.resortweather.source.WeatherSourceType;

/**
 * @param stale 갱신이 실패해 직전 값을 내보내는 중이면 true
 */
public record ResortWeatherResponse(
        String resortCode,
        BigDecimal temperature,
        String sky,
        OffsetDateTime observedAt,
        WeatherSourceType source,
        boolean stale) {

    public static ResortWeatherResponse of(String resortCode, WeatherSnapshot snapshot) {
        WeatherReading reading = snapshot.reading();
        return new ResortWeatherResponse(
                resortCode,
                reading.temperature(),
                reading.sky(),
                reading.observedAt(),
                reading.source(),
                snapshot.failedAt() != null);
    }
}
