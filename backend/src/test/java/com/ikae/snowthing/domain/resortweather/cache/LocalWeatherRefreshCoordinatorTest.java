package com.ikae.snowthing.domain.resortweather.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LocalWeatherRefreshCoordinatorTest {

    private static final String KEY = WeatherSnapshotStore.key("HIGH1");

    @Test
    @DisplayName("같은 키를 동시에 요청하면 수집은 한 번만 하고 결과를 같이 받는다")
    void concurrentLoadsShareOneFetch() throws Exception {
        LocalWeatherRefreshCoordinator coordinator =
                new LocalWeatherRefreshCoordinator(Runnable::run);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WeatherSnapshot result = new WeatherSnapshot(null, null, Instant.EPOCH);
        Supplier<WeatherSnapshot> slowLoader =
                () -> {
                    calls.incrementAndGet();
                    started.countDown();
                    await(release);
                    return result;
                };

        ExecutorService requests = Executors.newFixedThreadPool(2);
        try {
            Future<WeatherSnapshot> first =
                    requests.submit(() -> coordinator.loadNow(KEY, slowLoader));
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            Future<WeatherSnapshot> second =
                    requests.submit(() -> coordinator.loadNow(KEY, slowLoader));
            Thread.sleep(100);
            release.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS)).isSameAs(result);
            assertThat(second.get(2, TimeUnit.SECONDS)).isSameAs(result);
            assertThat(calls).hasValue(1);
        } finally {
            requests.shutdownNow();
        }
    }

    @Test
    @DisplayName("실행기가 가득 차 거절되면 갱신을 건너뛰고 다음 요청은 다시 시도할 수 있다")
    void rejectedRefreshIsSkipped() {
        AtomicInteger calls = new AtomicInteger();
        LocalWeatherRefreshCoordinator rejecting =
                new LocalWeatherRefreshCoordinator(
                        runnable -> {
                            throw new java.util.concurrent.RejectedExecutionException("full");
                        });

        rejecting.refreshInBackground(
                KEY,
                () -> {
                    calls.incrementAndGet();
                    return null;
                });
        WeatherSnapshot loaded =
                rejecting.loadNow(
                        KEY,
                        () -> {
                            calls.incrementAndGet();
                            return new WeatherSnapshot(null, null, Instant.EPOCH);
                        });

        assertThat(loaded).isNotNull();
        assertThat(calls).hasValue(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
