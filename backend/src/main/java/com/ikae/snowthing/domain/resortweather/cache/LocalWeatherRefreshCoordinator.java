package com.ikae.snowthing.domain.resortweather.cache;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.resortweather.config.ResortWeatherConfig;

import lombok.extern.slf4j.Slf4j;

/** 진행 중인 수집을 키별 {@link CompletableFuture}로 공유한다. 단일 서버에서만 유효하다. */
@Slf4j
@Component
public class LocalWeatherRefreshCoordinator implements WeatherRefreshCoordinator {

    /** 공식 소스와 기상청을 차례로 시도하는 최악 시간(각 연결 1초 + 읽기 2초)보다 조금 길다. */
    private static final long JOIN_TIMEOUT_SECONDS = 7;

    private final ConcurrentMap<String, CompletableFuture<WeatherSnapshot>> inFlight =
            new ConcurrentHashMap<>();
    private final Executor executor;

    public LocalWeatherRefreshCoordinator(
            @Qualifier(ResortWeatherConfig.REFRESH_EXECUTOR) Executor executor) {
        this.executor = executor;
    }

    @Override
    public WeatherSnapshot loadNow(String key, Supplier<WeatherSnapshot> loader) {
        CompletableFuture<WeatherSnapshot> mine = new CompletableFuture<>();
        CompletableFuture<WeatherSnapshot> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return await(running);
        }
        run(key, mine, loader);
        return await(mine);
    }

    @Override
    public void refreshInBackground(String key, Supplier<WeatherSnapshot> loader) {
        CompletableFuture<WeatherSnapshot> mine = new CompletableFuture<>();
        if (inFlight.putIfAbsent(key, mine) != null) {
            return;
        }
        try {
            executor.execute(() -> run(key, mine, loader));
        } catch (RejectedExecutionException exception) {
            inFlight.remove(key, mine);
            mine.complete(null);
            log.debug("기온 미리 갱신을 건너뜁니다. 실행기가 가득 찼습니다. key={}", key);
        }
    }

    private void run(
            String key,
            CompletableFuture<WeatherSnapshot> future,
            Supplier<WeatherSnapshot> loader) {
        try {
            future.complete(loader.get());
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        } finally {
            inFlight.remove(key, future);
        }
    }

    private WeatherSnapshot await(CompletableFuture<WeatherSnapshot> future) {
        try {
            return future.get(JOIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException exception) {
            return null;
        }
    }
}
