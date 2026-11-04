package com.ikae.snowthing.domain.resortweather.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortweather.cache.WeatherRefreshCoordinator;
import com.ikae.snowthing.domain.resortweather.cache.WeatherSnapshot;
import com.ikae.snowthing.domain.resortweather.cache.WeatherSnapshotStore;
import com.ikae.snowthing.domain.resortweather.dto.ResortWeatherResponse;
import com.ikae.snowthing.domain.resortweather.source.ResortWeatherSource;
import com.ikae.snowthing.domain.resortweather.source.ResortWeatherSourceRegistry;
import com.ikae.snowthing.domain.resortweather.source.WeatherFetchException;
import com.ikae.snowthing.domain.resortweather.source.WeatherReading;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

/**
 * 요청이 있을 때만 수집하고, 값의 나이로 응답 방식을 정한다(stale-while-revalidate).
 *
 * <ul>
 *   <li>8분 미만: 저장된 값을 그대로 응답
 *   <li>8~30분: 저장된 값을 바로 응답하고 백그라운드에서 한 번 갱신
 *   <li>30분 이상이거나 없음: 요청 스레드에서 수집. 같은 리조트 동시 요청은 한 번만 수집
 *   <li>수집이 모두 실패하면 5분 동안 다시 시도하지 않는다
 * </ul>
 */
@Slf4j
@Service
public class ResortWeatherService {

    static final Duration FRESH_FOR = Duration.ofMinutes(8);
    static final Duration USABLE_FOR = Duration.ofMinutes(30);
    static final Duration FAILURE_BACKOFF = Duration.ofMinutes(5);
    private static final Duration RESORT_LOOKUP_CACHE = Duration.ofHours(1);

    private final ResortRepository resortRepository;
    private final ResortWeatherSourceRegistry registry;
    private final WeatherSnapshotStore store;
    private final WeatherRefreshCoordinator coordinator;
    private final Clock clock;
    private final Cache<String, Boolean> activeResorts =
            Caffeine.newBuilder().expireAfterWrite(RESORT_LOOKUP_CACHE).maximumSize(64).build();

    @Autowired
    public ResortWeatherService(
            ResortRepository resortRepository,
            ResortWeatherSourceRegistry registry,
            WeatherSnapshotStore store,
            WeatherRefreshCoordinator coordinator) {
        this(resortRepository, registry, store, coordinator, Clock.systemUTC());
    }

    ResortWeatherService(
            ResortRepository resortRepository,
            ResortWeatherSourceRegistry registry,
            WeatherSnapshotStore store,
            WeatherRefreshCoordinator coordinator,
            Clock clock) {
        this.resortRepository = resortRepository;
        this.registry = registry;
        this.store = store;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    /** 활성 리조트가 아니면 RESORT_NOT_FOUND. 보여 줄 값이 없으면 빈 값. */
    public Optional<ResortWeatherResponse> getWeather(String resortCode) {
        requireActiveResort(resortCode);
        List<ResortWeatherSource> sources = registry.sourcesFor(resortCode);
        if (sources.isEmpty()) {
            return Optional.empty();
        }
        String key = WeatherSnapshotStore.key(resortCode);
        Instant now = clock.instant();
        WeatherSnapshot snapshot = store.get(key);

        if (isUsable(snapshot, now)) {
            boolean aging = !now.isBefore(snapshot.fetchedAt().plus(FRESH_FOR));
            if (aging && !recentlyFailed(snapshot, now)) {
                coordinator.refreshInBackground(key, () -> collect(resortCode, sources, key));
            }
            return Optional.of(ResortWeatherResponse.of(resortCode, snapshot));
        }
        if (recentlyFailed(snapshot, now)) {
            return Optional.empty();
        }
        WeatherSnapshot loaded = coordinator.loadNow(key, () -> collect(resortCode, sources, key));
        if (!isUsable(loaded, clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(ResortWeatherResponse.of(resortCode, loaded));
    }

    private WeatherSnapshot collect(
            String resortCode, List<ResortWeatherSource> sources, String key) {
        Instant now = clock.instant();
        for (ResortWeatherSource source : sources) {
            try {
                WeatherReading reading = source.fetch(now);
                WeatherSnapshot snapshot = WeatherSnapshot.success(reading, now);
                store.put(key, snapshot);
                return snapshot;
            } catch (WeatherFetchException exception) {
                // 같은 리조트는 실패 후 5분 동안 다시 수집하지 않으므로 이 로그도 그 이상 자주 찍히지 않는다.
                log.warn(
                        "리조트 기온 수집 실패 resort={} source={} reason={}",
                        resortCode,
                        source.type(),
                        exception.getMessage());
            }
        }
        WeatherSnapshot failed = WeatherSnapshot.failure(store.get(key), now);
        store.put(key, failed);
        return failed;
    }

    private void requireActiveResort(String resortCode) {
        Boolean active =
                activeResorts.get(
                        resortCode,
                        code ->
                                resortRepository
                                        .findByCode(code)
                                        .map(Resort::isActive)
                                        .orElse(false));
        if (!Boolean.TRUE.equals(active)) {
            throw new CustomException(ErrorCode.RESORT_NOT_FOUND);
        }
    }

    private static boolean isUsable(WeatherSnapshot snapshot, Instant now) {
        return snapshot != null
                && snapshot.reading() != null
                && now.isBefore(snapshot.fetchedAt().plus(USABLE_FOR));
    }

    private static boolean recentlyFailed(WeatherSnapshot snapshot, Instant now) {
        return snapshot != null
                && snapshot.failedAt() != null
                && now.isBefore(snapshot.failedAt().plus(FAILURE_BACKOFF));
    }
}
