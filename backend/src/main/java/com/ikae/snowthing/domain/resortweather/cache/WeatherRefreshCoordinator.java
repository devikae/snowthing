package com.ikae.snowthing.domain.resortweather.cache;

import java.util.function.Supplier;

/**
 * 같은 키의 수집이 동시에 여러 번 나가지 않게 조정한다. 지금은 JVM 안에서만 합치고, Redis로 옮기면 {@code SET NX PX} 락으로 서버 간에도 한 대만
 * 수집하게 바꾼다(ADR-1901).
 */
public interface WeatherRefreshCoordinator {

    /** 호출한 스레드가 결과를 기다린다. 이미 같은 키를 수집 중이면 그 결과를 같이 받는다. */
    WeatherSnapshot loadNow(String key, Supplier<WeatherSnapshot> loader);

    /** 기다리지 않는다. 이미 수집 중이거나 실행기가 가득 차면 아무것도 하지 않는다. */
    void refreshInBackground(String key, Supplier<WeatherSnapshot> loader);
}
