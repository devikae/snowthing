package com.ikae.snowthing.domain.resortweather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortweather.cache.InMemoryWeatherSnapshotStore;
import com.ikae.snowthing.domain.resortweather.cache.LocalWeatherRefreshCoordinator;
import com.ikae.snowthing.domain.resortweather.dto.ResortWeatherResponse;
import com.ikae.snowthing.domain.resortweather.source.ResortWeatherSource;
import com.ikae.snowthing.domain.resortweather.source.ResortWeatherSourceRegistry;
import com.ikae.snowthing.domain.resortweather.source.WeatherFetchException;
import com.ikae.snowthing.domain.resortweather.source.WeatherReading;
import com.ikae.snowthing.domain.resortweather.source.WeatherSourceType;
import com.ikae.snowthing.global.exception.CustomException;

/** 백그라운드 갱신은 호출 스레드에서 바로 실행되게 해 시간 경계를 결정적으로 검증한다. */
class ResortWeatherServiceTest {

    private static final String RESORT = "HIGH1";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T09:00:00Z"));
    private final FakeSource official = new FakeSource(WeatherSourceType.OFFICIAL);
    private final FakeSource kma = new FakeSource(WeatherSourceType.KMA);
    private ResortRepository resortRepository;
    private ResortWeatherSourceRegistry registry;
    private ResortWeatherService service;

    @BeforeEach
    void setUp() {
        resortRepository = mock(ResortRepository.class);
        Resort active = mock(Resort.class);
        when(active.isActive()).thenReturn(true);
        when(resortRepository.findByCode(RESORT)).thenReturn(Optional.of(active));
        registry = mock(ResortWeatherSourceRegistry.class);
        when(registry.sourcesFor(RESORT)).thenReturn(List.of(official, kma));
        service =
                new ResortWeatherService(
                        resortRepository,
                        registry,
                        new InMemoryWeatherSnapshotStore(),
                        new LocalWeatherRefreshCoordinator(Runnable::run),
                        clock);
    }

    @Test
    @DisplayName("8분 안에 다시 요청하면 외부 호출 없이 저장된 값을 준다")
    void freshValueIsServedWithoutFetching() {
        official.willReturn("1.0");

        ResortWeatherResponse first = service.getWeather(RESORT).orElseThrow();
        clock.advance(Duration.ofMinutes(7).plusSeconds(59));
        ResortWeatherResponse second = service.getWeather(RESORT).orElseThrow();

        assertThat(first.temperature()).isEqualByComparingTo("1.0");
        assertThat(first.source()).isEqualTo(WeatherSourceType.OFFICIAL);
        assertThat(second.temperature()).isEqualByComparingTo("1.0");
        assertThat(official.calls).isEqualTo(1);
        assertThat(kma.calls).isZero();
    }

    @Test
    @DisplayName("8~30분 사이에는 기존 값을 바로 주고 백그라운드에서 한 번 갱신한다")
    void agingValueIsServedWhileRefreshing() {
        official.willReturn("1.0");
        service.getWeather(RESORT);

        clock.advance(Duration.ofMinutes(8));
        official.willReturn("2.0");
        ResortWeatherResponse served = service.getWeather(RESORT).orElseThrow();
        ResortWeatherResponse next = service.getWeather(RESORT).orElseThrow();

        assertThat(served.temperature()).isEqualByComparingTo("1.0");
        assertThat(next.temperature()).isEqualByComparingTo("2.0");
        assertThat(official.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("공식 소스가 실패하면 기상청 값으로 응답한다")
    void fallsBackToKma() {
        official.willFail();
        kma.willReturn("-2.5");

        ResortWeatherResponse response = service.getWeather(RESORT).orElseThrow();

        assertThat(response.source()).isEqualTo(WeatherSourceType.KMA);
        assertThat(response.temperature()).isEqualByComparingTo("-2.5");
        assertThat(response.stale()).isFalse();
    }

    @Test
    @DisplayName("모두 실패하고 이전 값이 없으면 빈 값이고, 5분 동안 다시 수집하지 않는다")
    void failureIsBackedOffForFiveMinutes() {
        official.willFail();
        kma.willFail();

        assertThat(service.getWeather(RESORT)).isEmpty();
        clock.advance(Duration.ofMinutes(4));
        assertThat(service.getWeather(RESORT)).isEmpty();
        assertThat(official.calls).isEqualTo(1);

        clock.advance(Duration.ofMinutes(1));
        official.willReturn("3.0");
        assertThat(service.getWeather(RESORT)).isPresent();
        assertThat(official.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("갱신이 실패하면 30분까지 직전 값을 stale로 주고, 그 뒤에는 버린다")
    void failedRefreshKeepsPreviousValueUntilThirtyMinutes() {
        official.willReturn("1.0");
        service.getWeather(RESORT);

        clock.advance(Duration.ofMinutes(9));
        official.willFail();
        kma.willFail();
        service.getWeather(RESORT);
        ResortWeatherResponse stale = service.getWeather(RESORT).orElseThrow();

        assertThat(stale.temperature()).isEqualByComparingTo("1.0");
        assertThat(stale.stale()).isTrue();
        assertThat(official.calls).isEqualTo(2);

        clock.advance(Duration.ofMinutes(21));
        assertThat(service.getWeather(RESORT)).isEmpty();
    }

    @Test
    @DisplayName("30분이 지난 값은 요청 스레드에서 새로 수집한다")
    void expiredValueIsReloaded() {
        official.willReturn("1.0");
        service.getWeather(RESORT);

        clock.advance(Duration.ofMinutes(30));
        official.willReturn("5.0");

        assertThat(service.getWeather(RESORT).orElseThrow().temperature())
                .isEqualByComparingTo("5.0");
    }

    @Test
    @DisplayName("소스가 없는 리조트는 외부 호출 없이 빈 값이다")
    void resortWithoutSourcesIsEmpty() {
        when(registry.sourcesFor(RESORT)).thenReturn(List.of());

        assertThat(service.getWeather(RESORT)).isEmpty();
        assertThat(official.calls).isZero();
    }

    @Test
    @DisplayName("활성 리조트가 아니면 RESORT_NOT_FOUND")
    void inactiveResortIsNotFound() {
        Resort inactive = mock(Resort.class);
        when(resortRepository.findByCode("CLOSED")).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.getWeather("CLOSED")).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.getWeather("UNKNOWN")).isInstanceOf(CustomException.class);
    }

    private static final class FakeSource implements ResortWeatherSource {

        private final WeatherSourceType type;
        private final Deque<String> results = new ArrayDeque<>();
        private int calls;

        private FakeSource(WeatherSourceType type) {
            this.type = type;
        }

        void willReturn(String temperature) {
            results.clear();
            results.push(temperature);
        }

        void willFail() {
            results.clear();
        }

        @Override
        public WeatherSourceType type() {
            return type;
        }

        @Override
        public WeatherReading fetch(Instant now) {
            calls++;
            String temperature = results.peek();
            if (temperature == null) {
                throw new WeatherFetchException("fake failure");
            }
            return new WeatherReading(
                    new BigDecimal(temperature), null, now.atOffset(ZoneOffset.ofHours(9)), type);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
