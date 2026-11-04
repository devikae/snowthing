package com.ikae.snowthing.domain.resortweather.cache;

import java.time.Instant;

import com.ikae.snowthing.domain.resortweather.source.WeatherReading;

/**
 * 저장소에 두는 값. Redis에 JSON으로 그대로 넣을 수 있게 불변 record로 둔다.
 *
 * @param reading 마지막으로 성공한 값. 한 번도 성공하지 못했으면 null
 * @param fetchedAt reading을 받은 시각. 나이 판단 기준
 * @param failedAt 마지막 수집 실패 시각. 성공 후에는 null
 */
public record WeatherSnapshot(WeatherReading reading, Instant fetchedAt, Instant failedAt) {

    public static WeatherSnapshot success(WeatherReading reading, Instant now) {
        return new WeatherSnapshot(reading, now, null);
    }

    /** 실패해도 직전 값은 유지한다. 직전 값이 없으면 실패 표시만 남는다. */
    public static WeatherSnapshot failure(WeatherSnapshot previous, Instant now) {
        if (previous == null || previous.reading() == null) {
            return new WeatherSnapshot(null, null, now);
        }
        return new WeatherSnapshot(previous.reading(), previous.fetchedAt(), now);
    }
}
