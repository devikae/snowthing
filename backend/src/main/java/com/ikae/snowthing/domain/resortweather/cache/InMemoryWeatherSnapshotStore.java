package com.ikae.snowthing.domain.resortweather.cache;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class InMemoryWeatherSnapshotStore implements WeatherSnapshotStore {

    static final Duration RETENTION = Duration.ofMinutes(30);

    private final Cache<String, WeatherSnapshot> cache =
            Caffeine.newBuilder().expireAfterWrite(RETENTION).maximumSize(64).build();

    @Override
    public WeatherSnapshot get(String key) {
        return cache.getIfPresent(key);
    }

    @Override
    public void put(String key, WeatherSnapshot snapshot) {
        cache.put(key, snapshot);
    }
}
