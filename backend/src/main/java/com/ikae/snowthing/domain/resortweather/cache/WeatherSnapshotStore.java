package com.ikae.snowthing.domain.resortweather.cache;

/**
 * 리조트 기온 스냅샷 저장소. 지금은 서버 메모리 구현만 있고, 서버가 여러 대가 되면 같은 키 규칙으로 Redis 구현을 추가한다(ADR-1901). 저장소는 보관 기간만
 * 책임지고, 신선도 판단은 서비스가 {@code fetchedAt}으로 한다.
 */
public interface WeatherSnapshotStore {

    WeatherSnapshot get(String key);

    void put(String key, WeatherSnapshot snapshot);

    static String key(String resortCode) {
        return "resort-weather:v1:" + resortCode;
    }
}
